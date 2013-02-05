# Getting Started
This guide provides a summary of how to get started writing a provisioning server and a SSO service. Code samples are in Ruby using the [Grape API Framework](https://github.com/intridea/grape).

The outline for what we need to do is as follows:

1. Setup Lucid to target our local server
2. Create an object which is responsible for handling provisioning in our database.
3. Create an Provisioning Service API that StackMob will hit when a user adds, changes, or deletes the plan for a module.
4. Lastly, we'll create an SSO endpoint which is used to authenticate a user who has previously been provisioned


Let's set up Lucid to target our local server. We're going to create a service at http://localhost:3000/third-party/stackmob that passes the Lucid tests, so  create the following `lucid.properties` configuration:

```
protocol=http
provisionPath=localhost/third-party
port=3000
plans=planA,planB,planC
moduleId=asdf
password=jkl
salt=FooBar
ssoPath=localhost:3000/third-party/stackmob/auth/sso
```

# Provisioning
When a user adds, deletes, or changes the plan for a module, StackMob will hit your API. It is your responsibility to create the appropriate accounts in your database and associate them with StackMob. The information passed to you will be the ID of the StackMob app being provisioned, the email address of the user who added the module, and the plan which the user signed up for.

When creating a new account in your database, you should tie the account to the provisioning ID so multiple team members on that app can use your dashboard. You can also tie the provisioning ID to the email address of the user. However, because the user will not be able to set a password, you could end up in a situation where john@smith.com has an account on your service already, then tries to provision an account. Because StackMob will send the email address john@smith.com, you'll have an account conflict -- the suggested solution is to discard or manipulate the email address provided if there is a conflict.

Below is a sample implementation of a Ruby class that is responsible for provisioning. We'll use this class when we write our Provisioning Service below.

```ruby
module StackMob
  class AppProvisioner
    # Creates a User fro a StackMob request
    # @param stackmob_id String ID from StackMob
    # @param email The String user's email address
    # @param plan The String plan the user has chosen on the StackMob dashboard
    # @exception ArgumentError if the stackmob_id has been provisioned already
    # @return Created User object
    def provision(stackmob_id, email, plan)
      Rails.logger.info("Provisioning StackMob account from #{stackmob_id}, #{email}, #{plan}")
      if provisioned?(stackmob_id)
        raise ArgumentError.new("User with StackMob ID #{stackmob_id} already exists.")
      end

      user = User.create(:email => adjusted_email(email), :stackmob_id => stackmob_id, :plan => plan)
      return user
    end

    # Removes a User's linkage to StackMob.
    # @exception ArgumentError if the stackmob_id has not been provisioned
    # @param stackmob_id The string StackMob ID for the User
    def deprovision(stackmob_id)
      Rails.logger.info("Deprovisioning StackMob account #{stackmob_id}")
      user = User.where(:stackmob_id => stackmob_id).first

      if user.nil?
        raise ArgumentError.new("Invalid stackmob_id: #{stackmob_id}")
      end

      user.update_attributes(:stackmob_id => nil)
    end

    # @exception ArgumentError if the stackmob_id has not been provisioned
    # @param stackmob_id The string StackMob ID for the User
    # @param new_plan The string name of the new plan the user is moving to
    def change_plans(stackmob_id, new_plan)
      Rails.logger.info("Changing StackMob plan for user #{stackmob_id} to #{new_plan}")
      user = User.where(:stackmob_id => stackmob_id).first

      if user.nil?
        raise ArgumentError.new("Invalid stackmob_id: #{stackmob_id}")
      end

      user.update_attributes(:plan => new_plan)
    end

    private
    # @param stackmob_id The string StackMob ID for the User
    # @return Whether or not the stackmob_id has already been provisioned
    def provisioned?(stackmob_id)
      return User.where(:stackmob_id => stackmob_id).exists?
    end

    # StackMob will pass us an email address for their user and we need to create a User account
    # for them. Because there is no way for them to set a password (since it'll be single-sign-on from their dashboard)
    # we should tweak the email address so it's unique. This is suboptimal, but we can't prompt the user for information
    # and can't expose a security exploit by allowing anyone to modify an account by signing up for a StackMob account with
    # the same email address
    # @param email String email address
    # @return email The updated email address
    def adjusted_email(email)
      username, domain = email.split("@")
      rand = Random.rand(10000) + 1
      return "#{username}+stackmob#{rand}@#{domain}"
    end
  end
end
``` 

# Provisioning Service API
Now that we have our object which can handle provisioning on our end, we need to write our API.

StackMob will make provisioning requests to your Provisioning Service API whenever a user adds, deletes, or changes the plan for a module.

The content-type of the Provisioning Service must be `application/json;charset=utf-8`. The charset utf-8 must be included in the content-type: simply having `application/json` is not sufficient.

The complete code sample for the Provisioning Service API is below.

## Authentication
All endpoints in the provisioning service must be protected via basic authentication. The module id will be used as the username and the password as the password.

That is, when StackMob or Lucid hits your server, the following header will be sent:

Header: 
```
Authorization: Basic <base64 auth of module-id:password>
```

If authentication fails, the response should be a 401 not authorized.

## Error Handling

In the case of a 40x or 50x response for any of the provisioning endpoints the body of the response should be formatted like below if a body exists:

Content-Type: 
```
application/json;charset=utf-8
```

Body: 
```json
{"errors": [ "<msg1>", "<msg2>", ... ]}
```

- errors (```List[String]```): list of error messages

## Provision Endpoint
When a user first provisions an app, StackMob will hit this endpoint.

**Request sent to your server will look like:**

URI: 
```
POST https://<path-prefix>/stackmob/provision
```

Content-Type: 
```
application/json;charset=utf-8
```

Body: 
```json
{"id": id, "plan": plan, "email": email}
```

- id (```String(256)```): uniquely identifies the stackmob app that is being provisioned
- plan (```String(256)```): uniquely identifies the plan the app signed up for
- email (```String(256)```): the e-mail address of the user who added the module

**Your server needs to respond with:**

Code: 
```
201 - Created, if the app was provisioned successfully
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
409 - Conflict, if the app has already been provisioned
50x - Internal Error, if an internal error occurred
```

Content-Type Header: 
```
application/json;charset=utf-8
```

Location Header: 
```
https://<path-prefix>/stackmob/provision/:id
```

Body: 
```json
{"config-vars": { key1: value1, key2: value2, ... }}
```

- config-vars (```Map[String, String]```): a map of key/value pairs that will be available via custom code at runtime

## Deprovision Endpoint
StackMob will hit this endpoint when a user deprovisions an app

**Request sent to your server will look like:**

URI: 
```
DELETE https://<path-prefix>/stackmob/provision/:id
```

Body: 
```json
None
```

**Your server needs to respond with:**

Code: 
```
204 - No Content, if the deprovision was successsful
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
404 - Not Found, if the app was not found
50x - Internal Error, if an internal error occurred
```

Body: 
```json
None
```

## Change Plan Endpoint
StackMob will hit this endpoint when a user changes his plan.

**Request sent to your server will look like:**

URI: 
```
PUT https://<path-prefix>/stackmob/provision/:id
```

Content-Type: 
```
application/json;charset=utf-8
```

Body: 
```json
{"plan": plan}
```

- plan (```String(256)```): uniquely identifies the plan the app changed to

**Your server needs to respond with:**

Code: 
```
204 - No Content, if the plan change was successful
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
404 - Not Found, if the app was not found
50x - Internal Error, if an internal error occurred
```

Body: 
```json
None
```

## Code Sample
Below is a working example of the Provision Service, written in Ruby using [Grape API Framework](https://github.com/intridea/grape).

```ruby
module StackMob
  class ProvisionService < Grape::API
    USERNAME = "asdf" # This is the lucid.properties value we are using, but you should update per environment
    PASSWORD = "jkl" # This is the lucid.properties value we are using, but you should update per environment
    WEBSITE_BASE_URL = "http://mysite.com" # Update this per environment
    STACKMOB_REQUIRED_CONTENT_TYPE = 'application/json;charset=utf-8'
    TESTING_LOCALLY = true

    format :json

    # StackMob requires charset=utf-8 on their provision json request. Without setting the default_format,
    # Grape is going to try to use :json to format it, which will return with a Content-Type of just
    # "application/json". This will cause StackMob to fail, so we need the explicit UTF-8 charset.
    content_type :jsonutf8, STACKMOB_REQUIRED_CONTENT_TYPE
    formatter :jsonutf8, lambda {|object, env| MultiJson.dump(object)}
    default_format :jsonutf8

    # Require that all endpoints use basic auth, per StackMob's documentation
    use Grape::Middleware::Auth::Basic do |username, password|
      username == USERNAME && password == PASSWORD
    end

    helpers do
      # When you provision an account, StackMob requires us to set a Location back to the edit resource URL.
      # This method sets a Location on the response header.
      # @param id The StackMob ID provided by StackMob
      def set_provision_location(id)
        base_url = WEBSITE_BASE_URL
        # StackMob's test app, Lucid, strips off the port from the assumed location and uses a double slash after
        # the hostname. As such, when we're testing this in development, we need to massage the base url.
        if TESTING_LOCALLY
          base_url = "http://localhost/"
        end
        location = "#{base_url}/third-party/stackmob/provision/#{id}"
        header('Location', location)
      end

      # Runs a code block for StackMob account modification: throws a 404 error if there is an ArgumentError, or
      # returns 204 if there are no problems
      def modify_account
        begin
          provisioner = StackMob::AppProvisioner.new
          yield provisioner

          # We need to return a 204 here per StackMob
          status(Rack::Utils::SYMBOL_TO_STATUS_CODE[:no_content])
          nil
        rescue ArgumentError => e
          error!(e.message, Rack::Utils::SYMBOL_TO_STATUS_CODE[:not_found])
        end
      end
    end

    # Provisions a new Developer/App/Company linked to a StackMob ID
    post :provision do
      content_type STACKMOB_REQUIRED_CONTENT_TYPE

      email = params[:email]
      id = params[:id]
      plan = params[:plan]
      set_provision_location(id)
      provisioner = StackMob::AppProvisioner.new

      # Per https://github.com/stackmob/lucid/blob/master/provisioning.md, throw a 409 error if the
      # StackMob ID has already been provisioned
      begin
        user = provisioner.provision(id, email, plan)
      rescue ArgumentError
        error!("An account with StackMob ID #{id} has already been provisioned", 409)
      end

      # Update these config vars accordingly
      {"config-vars" => {:user_id => user.id}}
    end

    # Unprovisions a StackMob ID
    delete "provision/:id" do
      modify_account do |provisioner|
        provisioner.deprovision(params[:id])
      end
    end

    # Changes account plans for the user with a given StackMob ID
    put "provision/:id" do
      modify_account do |provisioner|
        provisioner.change_plans(params[:id], params[:plan])
      end
    end
  end
end
```

If mounting via Rails, to mount at `/third-party/stackmob`, add this to your `routes.rb` file:

```ruby
mount StackMob::ProvisionService => "/third-party/stackmob"
```

# Single Sign On (SSO)
Now that we have our provision service, StackMob can provision accounts in our database. You'll now need a way for
StackMob to sign users into your site. This is accomplished by setting up an endpoint that checks for a shared secret
and signs a user in if it matches.

Code sample follows the specification.

**Request sent to your server:**

URI: 
```
POST https://<sso-path>
```

Content-Type: 
```
application/x-www-form-urlencoded
```

Body: 
```
id=<id>&email=<email>&token=<token>&timestamp=<timestamp>
```

- id (```String(256)```): uniquely identifies the stackmob app sso is being attempted for
- email (```String(256)```): the logged in user's e-mail
- token (```String(40)```): generated token based on ```sha1(id + ':' email ':' + salt + ':' + timestamp)```
- timestamp (```Long```): the timestamp in milliseconds, though it could come across as a String so cast accordingly

**Your server needs to respond with:**

Code: 
```
302 - Found, if the SSO request was validated successfully and the user should be logged in
400 - Bad Request, if the request was malformed
403 - Forbidden, if authorization failed (see notes below)
404 - Not Found, if the app is not found
50x - Internal Error, if an internal error occurred
```

Location Header: 
```
<redirect-url>
```

Body: 
```
None
```

Notes:

- If a timestamp is older than 5 minutes then a 403 should be returned.
- If the token computed via ```sha1(id + ':' email ':' + salt + ':' + timestamp)``` does not match the provided token then a 403 should be returned.
- If the timestamp and token are both valid then the user should be logged in.

## Code Sample
Below is a Rails controller action for authenticating users. This is done as a controller action instead of another Grape endpoint because StackMob will not use basic auth when sending to this URL, and since it is supposed to log a user in and redirect to an 
appropriate location, it conceptually is not a RESTful endpoint.

In your `routes.rb` file, add:

```ruby
match '/third-party/stackmob/auth/sso', :to => "stack_mob#sso", :via => :post
```

Then create `stack_mob_controller.rb`:

```ruby
require 'digest/sha1'
class StackMobController < ApplicationController
  SHARED_SECRET = "FooBar"  # This is the lucid.properties value we are using, but you should update per environment

  # Single Sign On endpoint for StackMob. This endpoint will sign in a User that has already been created from a
  # StackMob provisioned account. See StackMob::ProvisionService for the API which handles provisioning.
  #
  # See the SSO section at https://github.com/stackmob/lucid/blob/master/provisioning.md for more information.
  def sso
    stackmob_id = params[:id]
    email = params[:email]
    token = params[:token]
    timestamp = params[:timestamp].to_i()
    time = Time.at(timestamp)

    # The StackMob API documentation requires that you throw a 403 error if the timestamp given is older than 5 minutes
    # to avoid replay attacks.
    if Time.now - time > 5.minutes
      return render :status => :forbidden, :nothing => true
    end

    # Authentication method per https://github.com/stackmob/lucid/blob/master/provisioning.md
    is_valid = Digest::SHA1.hexdigest("#{stackmob_id}:#{email}:#{SHARED_SECRET}:#{timestamp}") == token

    # Per StackMob documentation, throw a 403 if we cannot authenticate against the StackMob SSO payload
    if !is_valid
      return render :status => :forbidden, :nothing => true
    end

    user = User.where(:stackmob_id => stackmob_id).first

    # Per StackMob documentation, throw a 404 if we do not have a provisioned account already configured
    if user.nil?
      return render :status => :not_found, :nothing => true
    end

    # If we reached here, then the authentication succeeded and we should actually sign the user in and head over to
    # the dashboard; note that we'll be iframe'd inside StackMob's dashboard here.
    #
    # Replace this code with the appropriate code to sign in a user. This is the code to be used with Devise
    sign_in(user)

    # Redirect to the appropriate location here now that a user is logged in
    redirect_to :controller => :home, :action => :index
  end
end
```

# Finishing Steps
At this point, you should have a working service that passes the Lucid tests. Next, contact StackMob with your provisioning hostname and SSO URL. StackMob will provide you with the shared secret salt for provisioning.

# Appendix
## Config Vars
Configuration variables are key/value pairs that are returned in the provisioning response that will be available at runtime in custom code. A config var service will be added to the SDK service provider in custom code. They can also be updated outside of the standard provisioning API flow via the StackMob endpoints below.

### Get

**Request:**

URI: 
```
GET https://partner.stackmob.com/config/:id
```

Accept: 
```
application/json;charset=utf-8
```

Body:
```json
None
```

**Response**:

Code: 
```
200 - OK, if the config vars were retrieved successfully
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
404 - Not Found, if the app is not found
50x - Internal Error, if an internal error occurred
```

Content-Type: 
```
application/json;charset=utf-8
```

Body: 
```json
{"config-vars": { key1: value1, key2: value2, ... }}
```

- config-vars (```Map[String, String]```): map of existing config vars

### Update

**Request:**

URI: 
```
PUT https://partner.stackmob.com/config/:id
```

Content-Type: 
```
application/json;charset=utf-8
```

Body: 
```json
{"config-vars": { key1: value1, key2: value2, ... }}
```

- config-vars (```Map[String, String]```): map of config vars to update or create

**Response:**

Code: 
```
204 - No Content, if the config vars were updated successfully
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
404 - Not Found, if the app is not found
50x - Internal Error, if an internal error occurred
```

Body: 
```json
None
```

### Delete

**Request:**

URI: 
```
DELETE https://partner.stackmob.com/config/:id/:key
```

Body: 
```json
None
```

**Response:**

Code: 
```
204 - No Content, if the config var was deleted successfully
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
404 - Not Found, if the app is not found
50x - Internal Error, if an internal error occurred
```

Body: 
```json
None
```

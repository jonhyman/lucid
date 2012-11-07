# Provisioning
StackMob will make provisioning requests to third-party module providers whenever a user adds, deletes, or changes the plan for a module. These third party module providers will be required to implement an API that matches the interface defined below.

## Authentication

Provisioning requests and config var service requests require HTTPS and HTTP basic authentication. The basic auth password, module id, and salt (see SSO) for the module will be available via the platform. The password and id will be used to generate a basic auth header as defined below:

Base64: 
```
<module-id>:<password>
```

Header: 
```
Authorization: Basic <base64>
```

If authentication fails, the response should be a 401 not authorized.

## Error Handling

In the case of a 40x or 50x response for any of the provisioning endpoints (not SSO) the body of the response should be formatted like below if a body exists:

Content-Type: 
```
application/json;charset=utf-8
```

Body: 
```json
{"errors": [ "<msg1>", "<msg2>", ... ]}
```

- errors (```List[String]```): list of error messages

## Provision
**Request:**

URI: 
```
POST https://<hostname>/stackmob/provision
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

**Response**:

Code: 
```
201 - Created, if the app was provisioned successfully
400 - Bad Request, if the request was malformed
401 - Unauthorized, if authorization failed
409 - Conflict, if the app has already been provisioned
50x - Internal Error, if an internal error occurred
```

Content-Type: 
```
application/json;charset=utf-8
```

Location: 
```
https://<hostname>/stackmob/provision/:id
```

Body: 
```json
{"config-vars": { key1: value1, key2: value2, ... }}
```

- config-vars (```Map[String, String]```): a map of key/value pairs that will be available via custom code at runtime

## Deprovision
**Request:**

URI: 
```
DELETE https://<hostname>/stackmob/provision/:id
```

Body: 
```json
None
```

**Response:**

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

## Change Plan
**Request:**

URI: 
```
PUT https://<hostname>/stackmob/provision/:id
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

**Response:**

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

## SSO

**Request:**

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
- timestamp (```Long```): the timestamp in milliseconds

**Response:**

Code: 
```
302 - Found, if the SSO request was validated successfully and the user should be logged in
400 - Bad Request, if the request was malformed
403 - Forbidden, if authorization failed (see notes below)
404 - Not Found, if the app is not found
50x - Internal Error, if an internal error occurred
```

Location: 
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

# Config Vars
Configuration variables are key/value pairs that are returned in the provisioning response that will be available at runtime in custom code. A config var service will be added to the SDK service provider in custom code. They can also be updated outside of the standard provisioning API flow via the StackMob endpoints below.

## Get

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

## Update

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

## Delete

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

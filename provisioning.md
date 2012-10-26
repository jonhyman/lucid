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

In the case of a 400 or 50x response for any of the provisioning endpoints (not SSO) the body of the response should be formatted like below:

Content-Type: 
```
application/json; charset=utf-8
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
application/json; charset=utf-8
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
201 - Created 
400 - Bad Request 
401 - Unauthorized 
409 - Conflict 
50x - Internal Error
```

Content-Type: 
```
application/json; charset=utf-8
```

Location: 
```
https://<hostname>/stackmob/provision/:id
```

Body: 
```json
{"config-vars": { key1: value1, key2: value2, ... }}
```

- config-vars (```Map[String, String]```): an optional map of key/value pairs that will be available via custom code at runtime

## Deprovision
**Request:**

URI: 
```
DELETE https://<hostname>/stackmob/provision/:id
```

Body: 
```json
N/A
```

**Response:**

Code: 
```
204 - No Content 
400 - Bad Request 
401 - Unauthorized 
404 - Not Found 
50x - Internal Error
```

Body: 
```json
N/A
```

## Change Plan
**Request:**

URI: 
```
PUT https://<hostname>/stackmob/provision/:id
```

Content-Type: 
```
application/json; charset=utf-8
```

Body: 
```json
{"plan": plan}
```

- plan (```String(256)```): uniquely identifies the plan the app changed to

**Response:**

Code: 
```
204 - No Content 
400 - Bad Request 
401 - Unauthorized 
404 - Not Found 
50x - Internal Error
```

Body: 
```json
N/A
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
- timestamp (```Long```): the timestamp

**Response:**

Code: 
```
302 - Found
400 - Bad Request 
403 - Forbidden 
404 - Not Found 
50x - Internal Error
```

Location: 
```
<redirect-url>
```

Body: 
```
N/A
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
application/json; charset=utf-8
```

Body:
```json
N/A
```

**Response**:

Code: 
```
200 - OK 
400 - Bad Request 
401 - Unauthorized 
404 - Not Found 
50x - Internal Error
```

Content-Type: 
```
application/json; charset=utf-8
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
application/json; charset=utf-8
```

Body: 
```json
{"config-vars": { key1: value1, key2: value2, ... }}
```

- config-vars (```Map[String, String]```): map of config vars to update or create

**Response:**

Code: 
```
204 - No Content 
400 - Bad Request 
401 - Unauthorized 
404 - Not Found 
50x - Internal Error
```

Body: 
```json
N/A
```

## Delete

**Request:**

URI: 
```
DELETE https://partner.stackmob.com/config/:id/:key
```

Body: 
```json
N/A
```

**Response:**

Code: 
```
204 - No Content 
400 - Bad Request 
401 - Unauthorized 
404 - Not Found 
50x - Internal Error
```

Body: 
```json
N/A
```

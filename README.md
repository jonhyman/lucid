# Lucid
=====
Lucid is a tool for StackMob partners to test their third party provisioning servers. The tests expect a provisioning server
to be running on localhost:8080 by default, but is also configurable. The tests will generate a number of different requests
and verify the responses are as expected.

## Documentation
The provisioning and config var API's are documented [here](https://github.com/stackmob/lucid/blob/master/provisioning.md)

## Installation
Lucid is written in Scala and can be installed via [Conscript](https://github.com/n8han/conscript).

### Mac
1. Install Conscript: ```curl https://raw.github.com/n8han/conscript/master/setup.sh | sh```
2. Edit .bash_profile: ```PATH=$PATH:~/bin```
3. Install Lucid: ```cs stackmob/lucid```

### Configuration
Create ```lucid.properties``` with the relevant values for your provisioning server.
* protocol - http or https (default: http)
* provisionPath - the provisioning hostname along with an optional path prefix (default: localhost)
* port - the port (default: 8080)
* plans - the plan names (default: planA,planB,planC)
* moduleId - the module id (default: changeme)
* password - the password (default: changeme)
* salt - the salt (default: changeme)
* ssoPath - the URL used for single sign on requests (default: localhost:8080)

Example:
```text
protocol=http
provisionPath=localhost
port=8080
plans=planA,planB,planC
moduleId=changeme
password=changeme
salt=changeme
ssoPath=localhost:8080
```

## Running
```lucid /path/to/lucid.properties```

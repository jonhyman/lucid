# Lucid
=====
Lucid is a tool for StackMob partners to test their third party provisioning servers. The tests are run using sbt and
expect a provisioning server to be running on localhost:8080 by default, but is also configurable. The tests will generate
a number of different requests and verify the responses are as expected.

## Documentation
The provisioning and config var API's are documented [here](https://github.com/stackmob/lucid/blob/master/provisioning.md)

## Installation
Lucid is written in Scala and the tests are run as [specs2](http://etorreborre.github.com/specs2/) specifications. Running
the specs requires [sbt](http://www.scala-sbt.org/). Below are the installation steps.

## Mac
1. install [Homebrew](http://mxcl.github.com/homebrew/)
2. ```brew install sbt```
3. ```git clone git@github.com:stackmob/lucid.git```
4. ```cd lucid```
5. ```sbt "test-only *.ProvisioningServerSpecs"```

Note, brew installs sbt >= 0.12.1 so if you're not using Hombrew ensure you have at least this version (e.g. if you're using Macports).

## Configuration
Edit ```src/test/resources/lucid.properties``` with the relevant values for your provisioning server.
* protocol - http or https (default: http)
* provisionPath - the provisioning hostname along with an optional path prefix (default: localhost)
* port - the port (default: 8080)
* plans - the plan names (default: planA,planB,planC)
* moduleId - the module id (default: changeme)
* password - the password (default: changeme)
* salt - the salt (default: changeme)
* ssoPath - the URL used for single sign on requests (default: localhost:8080)

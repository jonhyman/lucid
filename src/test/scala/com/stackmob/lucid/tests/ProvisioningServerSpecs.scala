package com.stackmob.lucid.tests

/**
 * Copyright 2012 StackMob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.stackmob.lucid._
import java.net.{HttpURLConnection, URI}
import java.util.Properties
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.specs2._

class ProvisioningServerSpecs
  extends Specification
  with ScalaCheck { override def is = stopOnFail ^ sequential                                                           ^
  "Provisioning Server Specs".title                                                                                     ^
  """
  Verify the functionality of the third party provisioning server.
  """                                                                                                                   ^
                                                                                                                        p^
  "Provisioning should => POST /provision/stackmob"                                                                     ^
    "Return 201 created if the provision was successful"                                                                ! provision().created ^
    "Return 401 not authorized if authorization fails"                                                                  ! provision().notAuthorized ^
    "Return 409 conflict if a plan exists for the given id"                                                             ! provision().conflict ^
                                                                                                                        endp^
  "Deprovisioning should => DELETE /provision/stackmob/:id"                                                             ^
    "Return 204 no content if the deprovision was successful"                                                           ! deprovision().noContent ^
    "Return 401 not authorized if authorization fails"                                                                  ! deprovision().notAuthorized ^
    "Return 404 not found if no plan exists for the given id"                                                           ! deprovision().notFound ^
                                                                                                                        endp^
  "Changing a plan should => PUT /provision/stackmob/:id"                                                               ^
    "Return 204 no content if the plan was changed"                                                                     ! change().noContent ^
    "Return 401 not authorized if authorization fails"                                                                  ! change().notAuthorized ^
    "Return 404 not found if no plan exists for the given id"                                                           ! change().notFound ^
                                                                                                                        endp^
  "Single sign on should => POST /:sso-path"                                                                            ^
    "Return 302 redirect if the single sign on was successful"                                                          ! sso().redirect ^
    "Return 403 forbidden if authentication fails"                                                                      ! sso().forbiddenAuth ^
    "Return 403 forbidden if the timestamp is older than five minutes"                                                  ! sso().forbiddenTimestamp ^
    "Return 404 not found if no plan exists for the given id"                                                           ! sso().notFound ^
                                                                                                                        endp^
  "Reprovisioning should => POST /provision/stackmob"                                                                   ^
    "Return 201 created if the provision was successful"                                                                ! reprovision().created ^
                                                                                                                        end

  // use scalacheck to randomize requests, but we don't want duplicate requests
  implicit val params = set(minTestsOk -> 1)

  case class sso() extends ProvisioningContext {

    def redirect = apply {
      forAllNoShrink(genProvisionRequest) { (request) =>
        val timestamp = System.currentTimeMillis
        val token = createToken(request.id, request.email, salt, timestamp)
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        (client must resultInProvisionResponse(request)) and
          (client must resultInSSOResponse(SSORequest(request.id, token, request.email, ssoPath, timestamp)))
      }
    }

    def forbiddenAuth = apply {
      forAllNoShrink(genSSORequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        client must resultInSSOError(request.copy(email = "invalid@invalid.com"), HttpURLConnection.HTTP_FORBIDDEN)
      }
    }

    def forbiddenTimestamp = apply {
      forAllNoShrink(genSSORequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        client must resultInSSOError(
          request.copy(token = createToken(request.id, request.email, salt, 1), timestamp = 1),
          HttpURLConnection.HTTP_FORBIDDEN
        )
      }
    }

    def notFound = apply {
      forAllNoShrink(genSSORequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        client must resultInSSOError(request, HttpURLConnection.HTTP_NOT_FOUND)
      }
    }

  }

  case class provision() extends ProvisioningContext {

    def created = apply {
      forAllNoShrink(genProvisionRequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        client must resultInProvisionResponse(request)
      }
    }

    def notAuthorized = apply {
      forAllNoShrink(genProvisionRequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = "incorrect")
        client must resultInProvisionError(request, HttpURLConnection.HTTP_UNAUTHORIZED)
      }
    }

    def conflict = apply {
      forAllNoShrink(genProvisionRequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        (client must resultInProvisionResponse(request)) and
          (client must resultInProvisionError(request, HttpURLConnection.HTTP_CONFLICT))
      }
    }

  }

  case class reprovision() extends ProvisioningContext {

    def created() = apply {
      forAllNoShrink(genProvisionRequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        ((client must resultInProvisionResponse(request)) and
          (client must resultInDeprovisionResponse(DeprovisionRequest(request.id))) and
          (client must resultInProvisionResponse(request)))
      }
    }

  }

  case class deprovision() extends ProvisioningContext {

    def noContent = apply {
      forAllNoShrink(genProvisionRequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        ((client must resultInProvisionResponse(request)) and
          (client must resultInDeprovisionResponse(DeprovisionRequest(request.id))))
      }
    }

    def notAuthorized = apply {
      forAllNoShrink(genDeprovisionRequest) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = "incorrect")
        client must resultInDeprovisionError(request, HttpURLConnection.HTTP_UNAUTHORIZED)
      }
    }

    def notFound = apply {
      forAllNoShrink(genDeprovisionRequest) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        client must resultInDeprovisionError(request, HttpURLConnection.HTTP_NOT_FOUND)
      }
    }

  }

  case class change() extends ProvisioningContext {

    def noContent = apply {
      forAllNoShrink(genProvisionRequestViaProps) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        (client must resultInProvisionResponse(request)) and
          (client must resultInChangePlanResponse(ChangePlanRequest(request.id, request.plan)))
      }
    }

    def notAuthorized = apply {
      forAllNoShrink(genChangePlanRequest) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = "incorrect")
        client must resultInChangePlanError(request, HttpURLConnection.HTTP_UNAUTHORIZED)
      }
    }

    def notFound = apply {
      forAllNoShrink(genChangePlanRequest) { (request) =>
        val client = new ProvisioningClient(host = hostname, protocol = protocol, port = port, moduleId = moduleId, password = password)
        client must resultInChangePlanError(request, HttpURLConnection.HTTP_NOT_FOUND)
      }
    }

  }

  trait ProvisioningContext extends CommonContext {

    lazy val genProvisionRequestViaProps: Gen[ProvisionRequest] = {
      for {
        id <- genNonEmptyAlphaStr
        plan <- Gen.oneOf(plans)
        email <- genEmail
      } yield {
        ProvisionRequest(id, plan, email)
      }
    }

    lazy val genSSORequestViaProps: Gen[SSORequest] = {
      for {
        id <- genNonEmptyAlphaStr
        email <- genEmail
      } yield {
        val timestamp = System.currentTimeMillis
        SSORequest(id, createToken(id, email, salt, timestamp), email, ssoPath, timestamp)
      }
    }

    private lazy val props = {
      val p = new Properties
      p.load(getClass.getClassLoader.getResourceAsStream("lucid.properties"))
      p
    }

    lazy val protocol = props.getProperty("protocol")
    lazy val hostname = props.getProperty("hostname")
    lazy val port = props.getProperty("port").toInt
    lazy val plans = props.getProperty("plans").split(",").toList
    lazy val moduleId = props.getProperty("moduleId")
    lazy val password = props.getProperty("password")
    lazy val ssoPath = new URI(props.getProperty("ssoPath"))
    lazy val salt = props.getProperty("salt")

  }

}

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
import java.net.HttpURLConnection
import java.util.Properties
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.specs2._

class ProvisioningServerSpecs
  extends Specification
  with ScalaCheck { override def is = sequential                                                                        ^
  "Provisioning Server Specs".title                                                                                     ^
  """
  Verify the functionality of the third party provisioning server.
  """                                                                                                                   ^
                                                                                                                        p^
  "Provisioning should => POST /provision/stackmob"                                                                     ^
    "Return 201 ok if the provision was successful"                                                                     ! provision().created ^
    "Return 401 not authorized if authorization fails"                                                                  ! provision().notAuthorized ^
    "Return 409 conflict if a plan exists for the given id"                                                             ! provision().conflict
                                                                                                                        endp^
  "Deprovisioning should => DELETE /provision/stackmob/:id"                                                             ^
    "Return 204 no content if the deprovision was successful"                                                           ! deprovision().noContent ^
    "Return 401 not authorized if authorization fails"                                                                  ! deprovision().notAuthorized ^
    "Return 404 not found if no plan exists for the given id"                                                           ! deprovision().notFound
                                                                                                                        endp^
  "Changing a plan should => PUT /provision/stackmob/:id"                                                               ^
    "Return 204 no content if the plan was changed"                                                                     ! change().noContent ^
    "Return 401 not authorized if authorization fails"                                                                  ! change().notAuthorized ^
    "Return 404 not found if no plan exists for the given id"                                                           ! change().notFound
                                                                                                                        endp^
  "Single sign on should => POST /:sso-path"                                                                            ^
    "Return 302 redirect if the single sign on was successful"                                                          ! pending ^
    "Return 403 forbidden if authentication fails"                                                                      ! pending ^
    "Return 404 not found if no plan exists for the given id"                                                           ! pending ^
    "Return 50x if there is an error handling the request"                                                              ! pending ^
                                                                                                                        end

  // use scalacheck to randomize requests, but we don't want duplicate requests
  implicit val params = set(minTestsOk -> 1)

  case class provision() extends ProvisioningContext {

    def created = apply {
      forAllNoShrink(genProvisionRequestWithPlan) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = password)
        client must resultInProvisionResponse(request)
      }
    }

    def notAuthorized = apply {
      forAllNoShrink(genProvisionRequestWithPlan) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = "incorrect")
        client must resultInProvisionError(request, HttpURLConnection.HTTP_UNAUTHORIZED)
      }
    }

    def conflict = apply {
      forAllNoShrink(genProvisionRequestWithPlan) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = password)
        (client must resultInProvisionResponse(request)) and
          (client must resultInProvisionError(request, HttpURLConnection.HTTP_CONFLICT))
      }
    }

  }

  case class deprovision() extends ProvisioningContext {

    def noContent = apply {
      forAllNoShrink(genProvisionRequestWithPlan) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = password)
        ((client must resultInProvisionResponse(request)) and
          (client must resultInDeprovisionResponse(DeprovisionRequest(request.id))))
      }
    }

    def notAuthorized = apply {
      forAllNoShrink(genDeprovisionRequest) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = "incorrect")
        client must resultInDeprovisionError(request, HttpURLConnection.HTTP_UNAUTHORIZED)
      }
    }

    def notFound = apply {
      forAllNoShrink(genDeprovisionRequest) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = password)
        client must resultInDeprovisionError(request, HttpURLConnection.HTTP_NOT_FOUND)
      }
    }

  }

  case class change() extends ProvisioningContext {

    def noContent = apply {
      forAllNoShrink(genProvisionRequestWithPlan) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = password)
        (client must resultInProvisionResponse(request)) and
          (client must resultInChangePlanResponse(ChangePlanRequest(request.id, request.plan)))
      }
    }

    def notAuthorized = apply {
      forAllNoShrink(genChangePlanRequest) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = "incorrect")
        client must resultInChangePlanError(request, HttpURLConnection.HTTP_UNAUTHORIZED)
      }
    }

    def notFound = apply {
      forAllNoShrink(genChangePlanRequest) { (request) =>
        val client = new ProvisioningClient(moduleId = moduleId, password = password)
        client must resultInChangePlanError(request, HttpURLConnection.HTTP_NOT_FOUND)
      }
    }

  }

  trait ProvisioningContext extends CommonContext {

    lazy val genProvisionRequestWithPlan: Gen[ProvisionRequest] = {
      for {
        id <- genNonEmptyAlphaStr
        plan <- Gen.oneOf(plans)
        email <- genEmail
      } yield {
        ProvisionRequest(id, plan, email)
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

  }

}

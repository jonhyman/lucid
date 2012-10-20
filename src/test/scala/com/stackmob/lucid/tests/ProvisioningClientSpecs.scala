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

import java.net.{HttpURLConnection, URI}
import com.stackmob.lucid._
import net.liftweb.json.{compact, render}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.scalaz.JsonScalaz._
import org.apache.http.HttpHeaders
import org.apache.http.message.BasicHeader
import org.scalacheck.Prop._
import org.specs2._
import ProvisioningClient._
import scalaz._
import scalaz.effects._
import Scalaz._

class ProvisioningClientSpecs
  extends Specification
  with ScalaCheck { override def is =

  "Provisioning Client Specs".title                                                                                     ^
  """
  The provisioning client specs verify the functionality of the provisioning client.
  """                                                                                                                   ^
                                                                                                                        p^
  "Provisioning should => POST /provision/stackmob"                                                                     ^
    "Return 201 ok if the provision was successful"                                                                     ! provision().created ^
    "Return 401 not authorized if authentication fails"                                                                 ! provision().notAuthorized ^
    "Return 409 conflict if a plan exists for the given id"                                                             ! provision().conflict ^
    "Return 50x if there is an error handling the request"                                                              ! provision().error ^
                                                                                                                        endp^
  "Deprovisioning should => DELETE /provision/stackmob/:id"                                                             ^
    "Return 204 no content if the deprovision was successful"                                                           ! deprovision().noContent ^
    "Return 401 not authorized if authentication fails"                                                                 ! deprovision().notAuthorized ^
    "Return 404 not found if no plan exists for the given id"                                                           ! deprovision().notFound ^
    "Return 50x if there is an error handling the request"                                                              ! deprovision().error ^
                                                                                                                        endp^
  "Changing a plan should => PUT /provision/stackmob/:id"                                                               ^
    "Return 204 no content if the plan was changed"                                                                     ! change().noContent ^
    "Return 401 not authorized if authentication fails"                                                                 ! change().notAuthorized ^
    "Return 404 not found if no plan exists for the given id"                                                           ! change().notFound ^
    "Return 50x if there is an error handling the request"                                                              ! change().error ^
                                                                                                                        end

  case class provision() extends CommonContext {

    def created = apply {
      forAll(genProvisionRequest, genNonEmptyAlphaStr, genMap, genNonEmptyAlphaStr) { (request, module, configVars, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.post(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_CREATED,
          body = compact(render(toJSON(InternalProvisionResponse(configVars)))).some,
          headers = List(new BasicHeader(HttpHeaders.LOCATION, "http://localhost/%s/%s".format(provisionURL, request.id)), contentTypeHeader).toNel
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.provision(request).unsafePerformIO.either must beRight.like {
          case response: ProvisionResponse => {
            (response.configVars must haveTheSameElementsAs(configVars)) and
              (response.location must beEqualTo(new URI("http://localhost/%s/%s".format(provisionURL, request.id))))
          }
        }
      }
    }

    def notAuthorized = apply {
      forAll(genProvisionRequest, genNonEmptyAlphaStr, genMap, genNonEmptyAlphaStr) { (request, module, configVars, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.post(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_UNAUTHORIZED,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.provision(request).unsafePerformIO.either must beLeft.like {
          case e: EmptyErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED)
        }
      }
    }

    def conflict = apply {
      forAll(genProvisionRequest, genNonEmptyAlphaStr, genMap, genNonEmptyAlphaStr) { (request, module, configVars, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.post(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_CONFLICT,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.provision(request).unsafePerformIO.either must beLeft.like {
          case e: EmptyErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_CONFLICT)
        }
      }
    }

    def error = apply {
      forAll(genProvisionRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr, genErrorMsgs) { (request, module, pwd, errors) =>
        val mockedClient = mock[HttpClient]
        mockedClient.post(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_INTERNAL_ERROR,
          body = compact(render((errorRootJSONKey -> toJSON(errors)))).some,
          headers = List(contentTypeHeader).toNel
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.provision(request).unsafePerformIO.either must beLeft.like {
          case e: FullErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR)
        }
      }
    }

  }

  case class deprovision() extends CommonContext {

    def noContent = apply {
      forAll(genDeprovisionRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr) { (request, module, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.delete(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_NO_CONTENT,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.deprovision(request).unsafePerformIO.either must beRight
      }
    }

    def notAuthorized = apply {
      forAll(genDeprovisionRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr) { (request, module, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.delete(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_UNAUTHORIZED,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.deprovision(request).unsafePerformIO.either must beLeft.like {
          case e: EmptyErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED)
        }
      }
    }

    def notFound = apply {
      forAll(genDeprovisionRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr) { (request, module, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.delete(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_NOT_FOUND,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.deprovision(request).unsafePerformIO.either must beLeft.like {
          case e: EmptyErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
        }
      }
    }

    def error = apply {
      forAll(genDeprovisionRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr, genErrorMsgs) { (request, module, pwd, errors) =>
        val mockedClient = mock[HttpClient]
        mockedClient.delete(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_INTERNAL_ERROR,
          body = compact(render((errorRootJSONKey -> toJSON(errors)))).some,
          headers = List(contentTypeHeader).toNel
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.deprovision(request).unsafePerformIO.either must beLeft.like {
          case e: FullErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR)
        }
      }
    }

  }

  case class change() extends CommonContext {

    def noContent = apply {
      forAll(genChangePlanRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr) { (request, module, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.put(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_NO_CONTENT,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.changePlan(request).unsafePerformIO.either must beRight
      }
    }

    def notAuthorized = apply {
      forAll(genChangePlanRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr) { (request, module, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.put(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_UNAUTHORIZED,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.changePlan(request).unsafePerformIO.either must beLeft.like {
          case e: EmptyErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED)
        }
      }
    }

    def notFound = apply {
      forAll(genChangePlanRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr) { (request, module, pwd) =>
        val mockedClient = mock[HttpClient]
        mockedClient.put(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_NOT_FOUND,
          body = None,
          headers = None
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.changePlan(request).unsafePerformIO.either must beLeft.like {
          case e: EmptyErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
        }
      }
    }

    def error = apply {
      forAll(genChangePlanRequest, genNonEmptyAlphaStr, genNonEmptyAlphaStr, genErrorMsgs) { (request, module, pwd, errors) =>
        val mockedClient = mock[HttpClient]
        mockedClient.put(any[HttpRequest]) returns HttpResponse(
          code = HttpURLConnection.HTTP_INTERNAL_ERROR,
          body = compact(render((errorRootJSONKey -> toJSON(errors)))).some,
          headers = List(contentTypeHeader).toNel
        ).pure[IO]
        val client = new ProvisioningClient(httpClient = mockedClient, moduleId = module, password = pwd)
        client.changePlan(request).unsafePerformIO.either must beLeft.like {
          case e: FullErrorResponse => e.code must beEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR)
        }
      }
    }

  }

}


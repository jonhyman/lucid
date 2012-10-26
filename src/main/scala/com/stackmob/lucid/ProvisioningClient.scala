package com.stackmob.lucid

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

import java.net.{HttpURLConnection, URLEncoder, URI}
import java.nio.charset.Charset
import net.liftweb.json.{compact, parse, render}
import net.liftweb.json.JsonAST._
import net.liftweb.json.scalaz.JsonScalaz._
import org.apache.commons.codec.binary.{StringUtils, Base64}
import org.apache.commons.lang
import org.apache.commons.validator.routines.EmailValidator
import org.apache.http.Consts._
import org.apache.http.Header
import org.apache.http.HttpHeaders
import org.apache.http.message.BasicHeader
import ProvisioningClient._
import scalaz._
import scalaz.effects._
import Scalaz._
import ValidationT._

/**
 * The Provisioning client is used to test the provisioning API of third party modules.
 *
 * @param host the hostname of the provisioning api
 * @param protocol the protocol
 * @param port the port
 * @param httpClient the http client to use
 * @param charset the charset
 * @param moduleId the module id
 * @param password the basic auth password for the third party module
 */
class ProvisioningClient(val host: String = "localhost",
                         val protocol: String = "http",
                         val port: Int = 8080,
                         val charset: Charset = UTF_8,
                         httpClient: HttpClient = new ApacheHttpClient,
                         moduleId: String,
                         password: String) {

  /**
   * Perform an single sign on request for an app.
   *
   * @param request the single sign on request
   * @return the single sign on response
   */
  def sso(request: SSORequest): IO[Validation[LucidError, SSOResponse]] = {
    (for {
      _ <- validateId(request.id)
      _ <- validateEmail(request.email)
      _ <- validateToken(request.token)
      _ <- validateTimestamp(request.timestamp)
      _ <- validateModuleId(moduleId)
      _ <- validatePassword(password)
      httpRequest <- validationT {
        HttpRequest(
          url = "%s://%s".format(protocol, request.path.toString),
          body = "id=%s&email=%s&token=%s&timestamp=%s".format(
            URLEncoder.encode(request.id, charset.toString),
            URLEncoder.encode(request.email, charset.toString),
            URLEncoder.encode(request.token, charset.toString),
            URLEncoder.encode(request.timestamp.toString, charset.toString)).some,
          headers = List(formURLEncodedContentTypeHeader).toNel
        ).success[LucidError].pure[IO]
      }
      response <- validationT {
        httpClient.post(httpRequest)
          .map(handleSSOResponse(HttpURLConnection.HTTP_MOVED_TEMP, _))
          .except(t => (UnexpectedErrorResponse(t.getMessage): LucidError).fail[SSOResponse].pure[IO])
      }
    } yield response).run
  }

  /**
   * Provision a module for an app.
   *
   * @param request the provisioning request
   * @return the provisioning response as an IO Validation
   */
  def provision(request: ProvisionRequest): IO[Validation[LucidError, ProvisionResponse]] = {
    (for {
      _ <- validateId(request.id)
      _ <- validatePlan(request.plan)
      _ <- validateEmail(request.email)
      _ <- validateModuleId(moduleId)
      _ <- validatePassword(password)
      httpRequest <- validationT {
        HttpRequest(
          url = "%s://%s:%s/%s".format(protocol, host, port, provisionURL),
          body = compact(render(toJSON(request))).some,
          headers = List(getBasicAuthHeader(request), jsonContentTypeHeader).toNel
        ).success[LucidError].pure[IO]
      }
      response <- validationT {
        httpClient.post(httpRequest)
          .map(handleProvisionResponse(HttpURLConnection.HTTP_CREATED, _))
          .except(t => (UnexpectedErrorResponse(t.getMessage): LucidError).fail[ProvisionResponse].pure[IO])
      }
    } yield response).run
  }

  /**
   * Deprovision a module for an app.
   *
   * @param request the deprovisioning request
   * @return Unit as an IO Validation
   */
  def deprovision(request: DeprovisionRequest): IO[Validation[LucidError, Unit]] = {
    (for {
      _ <- validateId(request.id)
      _ <- validateModuleId(moduleId)
      _ <- validatePassword(password)
      httpRequest <- validationT {
        HttpRequest(
          url = "%s://%s:%s/%s/%s".format(protocol, host, port, provisionURL, request.id),
          body = none,
          headers = List(getBasicAuthHeader(request)).toNel
        ).success[LucidError].pure[IO]
      }
      resp <- validationT {
        httpClient.delete(httpRequest)
          .map(handleResponse(HttpURLConnection.HTTP_NO_CONTENT, _))
          .except(t => (UnexpectedErrorResponse(t.getMessage): LucidError).fail[Unit].pure[IO])
      }
    } yield resp).run
  }

  /**
   * Change the plan for an app.
   *
   * @param request the change plan request
   * @return Unit as an IO Validation
   */
  def changePlan(request: ChangePlanRequest): IO[Validation[LucidError, Unit]] = {
    (for {
      _ <- validateId(request.id)
      _ <- validatePlan(request.plan)
      _ <- validateModuleId(moduleId)
      _ <- validatePassword(password)
      httpRequest <- validationT {
        HttpRequest(
          url = "%s://%s:%s/%s/%s".format(protocol, host, port, provisionURL, request.id),
          body = compact(render(toJSON(request))).some,
          headers = List(getBasicAuthHeader(request), jsonContentTypeHeader).toNel
        ).success[LucidError].pure[IO]
      }
      resp <- validationT {
        httpClient.put(httpRequest)
          .map(handleResponse(HttpURLConnection.HTTP_NO_CONTENT, _))
          .except(t => (UnexpectedErrorResponse(t.getMessage): LucidError).fail[Unit].pure[IO])
      }
    } yield resp).run
  }

  private def validateId(id: String): ValidationT[IO, LucidError, String] = {
    validateString(id, "Invalid id provided")
  }

  private def validatePlan(plan: String): ValidationT[IO, LucidError, String] = {
    validateString(plan, "Invalid plan provided")
  }

  private def validateModuleId(moduleId: String): ValidationT[IO, LucidError, String] = {
    validateString(moduleId, "Invalid module id provided")
  }

  private def validatePassword(password: String): ValidationT[IO, LucidError, String] = {
    validateString(password, "Invalid password provided")
  }

  private def validateToken(token: String): ValidationT[IO, LucidError, String] = {
    validateString(token, "Invalid token provided")
  }

  private def validateString(s: String, msg: String): ValidationT[IO, LucidError, String] = {
    validationT {
      (if (lang.StringUtils.isNotBlank(s)) {
        s.success[LucidError]
      } else {
        InputError(msg).fail
      }).pure[IO]
    }
  }

  private def validateEmail(email: String): ValidationT[IO, LucidError, String] = {
    validationT {
      (if (EmailValidator.getInstance(true).isValid(email)) {
        email.success[LucidError]
      } else {
        InputError("Invalid email provided").fail
      }).pure[IO]
    }
  }

  private def validateTimestamp(timestamp: Long): ValidationT[IO, LucidError, Long] = {
    validationT {
      (if (timestamp > 0) {
        timestamp.success[LucidError]
      } else {
        InputError("Invalid timestamp provided").fail
      }).pure[IO]
    }
  }

  private def getBasicAuthHeader(request: BasicAuthRequest): Header = {
    val authHeader = "%s:%s".format(moduleId, password)
    val encodedAuth = "Basic %s".format(StringUtils.newStringUtf8(Base64.encodeBase64(StringUtils.getBytesUtf8(authHeader), false)))
    new BasicHeader(HttpHeaders.AUTHORIZATION, encodedAuth)
  }

  private def handleResponse(expectedCode: Int, resp: HttpResponse): Validation[LucidError, Unit] = {
    if (resp.code === expectedCode) ().success else handleErrors(resp)
  }

  private def handleSSOResponse(expectedCode: Int, resp: HttpResponse): Validation[LucidError, SSOResponse] = {
    handleResponse(expectedCode, resp).flatMap(_ => validateLocationHeader(resp).flatMap(uri => SSOResponse(uri).success))
  }

  private def handleProvisionResponse(expectedCode: Int, resp: HttpResponse): Validation[LucidError, ProvisionResponse] = {
    handleResponseWithBody[InternalProvisionResponse](expectedCode, resp)
      .flatMap(r => validateLocationHeader(resp).flatMap(uri => ProvisionResponse(r.configVars, uri).success))
      .flatMap(validateContentType(_, resp))
  }

  private def handleResponseWithBody[T : JSONR](expectedCode: Int, resp: HttpResponse): Validation[LucidError, T] = {
    handleResponse(expectedCode, resp).flatMap { _ =>
      fromJSON[T](parse(~resp.body))
        .mapFailure(errors => FullErrorResponse(resp.code, toErrors(errors)))
        .flatMap(validateContentType(_, resp))
    }
  }

  private def handleErrors[T](resp: HttpResponse): Validation[LucidError, T] = {
    if (~resp.body.map(_.length) > 0) {
      fromJSON[Int => LucidError](parse(~resp.body))
        .mapFailure(errors => FullErrorResponse(resp.code, toErrors(errors)))
        .flatMap(r => validateContentType(r, resp).flatMap(f => f(resp.code).fail))
    } else {
      EmptyErrorResponse(resp.code).fail
    }
  }

  private def validateLocationHeader(resp: HttpResponse): Validation[LucidError, URI] = {
    validating(new URI(~resp.headers.flatMap(_.list.find(h => HttpHeaders.LOCATION.equalsIgnoreCase(h.getName)).map(_.getValue))))
      .mapFailure(_ => FullErrorResponse(resp.code, nel("Invalid or missing location header")))
  }

  private def validateContentType[T](r: T, resp: HttpResponse): Validation[LucidError, T] = {
    val h = resp.headers.flatMap(_.list.find(h => HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(h.getName)))
    if (~h.map(_.getValue.equalsIgnoreCase(jsonContentTypeHeader.getValue))) {
      r.success
    } else {
      UnexpectedErrorResponse("Invalid content type").fail
    }
  }

  private def toErrors(errors: NonEmptyList[Error]): NonEmptyList[String] = {
    errors map {
      case UnexpectedJSONError(_, _) => "Unexpected json"
      case NoSuchFieldError(name, _) => "Invalid field: %s".format(name)
      case UncategorizedError(_, desc, _) => "Invalid json: %s".format(desc)
    }
  }

  implicit val codeToErrorResponseJSONR = new JSONR[Int => LucidError] {
    override def read(json: JValue): Result[Int => LucidError] = {
      (field[List[String]](errorRootJSONKey)(json)).map(errors => {
        if (errors.length > 0) {
          FullErrorResponse(_, nel(errors.head, errors.tail))
        } else {
          EmptyErrorResponse(_)
        }
      })
    }
  }

}

object ProvisioningClient {
  val errorRootJSONKey = "errors"
  val provisionURL = "stackmob/provision"
  val jsonContentTypeHeader = new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
  val formURLEncodedContentTypeHeader = new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
}

sealed trait LucidError
sealed trait ErrorResponse extends LucidError

case class InputError(message: String) extends LucidError
case class EmptyErrorResponse(code: Int) extends ErrorResponse
case class UnexpectedErrorResponse(message: String) extends ErrorResponse
case class FullErrorResponse(code: Int, errors: NonEmptyList[String]) extends ErrorResponse

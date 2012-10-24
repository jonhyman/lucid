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
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.specs2.execute.{Result => SpecsResult}
import org.specs2.matcher.{MatchResult, Expectable, Matcher}
import org.specs2.mock.Mockito
import org.specs2.specification._
import ProvisioningClient._

trait CommonContext extends Around with Mockito {

  override def around[T <% SpecsResult](t: => T): SpecsResult = t

  lazy val genNonEmptyAlphaStr: Gen[String] = {
    for {
      size <- Gen.choose(1, 256)
      str <- Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString)
    } yield str
  }

  lazy val genNonEmptyStr: Gen[String] = {
    for {
      size <- Gen.choose(1, 256)
      str <- Gen.listOfN(size, arbitrary[Char]).map(_.mkString)
    } yield str
  }

  lazy val genErrorMsgs: Gen[List[String]] = Gen.listOf1(arbitrary[String])

  lazy val genMap: Gen[Map[String, String]] = {
    for {
      n <- Gen.posNum[Int]
      values <- Gen.listOfN[String](n, arbitrary[String])
      keys <- Gen.listOfN[String](n, arbitrary[String])
    } yield keys.zip(values).toMap
  }

  lazy val genEmail: Gen[String] = {
    for {
      sizePrefix <- Gen.choose(1, 127)
      sizeDomain <- Gen.choose(1, 124)
      prefix <- Gen.listOfN(sizePrefix, Gen.alphaNumChar).map(_.mkString)
      domain <- Gen.listOfN(sizeDomain, Gen.alphaNumChar).map(_.mkString)
      suffix <- Gen.oneOf(List(".com", ".net", ".org"))
    } yield "%s@%s%s".format(prefix, domain, suffix)
  }

  lazy val genProvisionRequest: Gen[ProvisionRequest] = {
    for {
      id <- genNonEmptyAlphaStr
      plan <- genNonEmptyStr
      email <- genEmail
    } yield {
      ProvisionRequest(id, plan, email)
    }
  }

  lazy val genDeprovisionRequest: Gen[DeprovisionRequest] = {
    for {
      id <- genNonEmptyAlphaStr
    } yield {
      DeprovisionRequest(id)
    }
  }

  lazy val genChangePlanRequest: Gen[ChangePlanRequest] = {
    for {
      id <- genNonEmptyAlphaStr
      plan <- genNonEmptyStr
    } yield {
      ChangePlanRequest(id, plan)
    }
  }

  def resultInProvisionResponse(request: ProvisionRequest) = new ClientHasProvisionResponse(request)

  def resultInDeprovisionResponse(request: DeprovisionRequest) = new ClientHasDeprovisionResponse(request)

  def resultInChangePlanResponse(request: ChangePlanRequest) = new ClientHasChangePlanResponse(request)

  def resultInProvisionError(request: ProvisionRequest, code: Int) = new ClientHasProvisionError(request, code)

  def resultInDeprovisionError(request: DeprovisionRequest, code: Int) = new ClientHasDeprovisionError(request, code)

  def resultInChangePlanError(request: ChangePlanRequest, code: Int) = new ClientHasChangePlanError(request, code)

  class ClientHasProvisionResponse(request: ProvisionRequest) extends Matcher[ProvisioningClient] {
    override def apply[S <: ProvisioningClient](r: Expectable[S]): MatchResult[S] = {
      val client = r.value
      val expectedURI = new URI("%s://%s/%s/%s".format(client.protocol, client.host, provisionURL, request.id)).toString
      val response = client.provision(request).unsafePerformIO
      response match {
        case scalaz.Success(s) =>
          result(
            s.location.toString.equalsIgnoreCase(expectedURI),
            "Provision response has config vars: %s and location: %s".format(s.configVars, s.location.toString),
            "Provision response has config vars: %s and location: %s, expected location: %s".format(s.configVars, s.location.toString, expectedURI),
            r
          )
        case scalaz.Failure(InputError(message)) =>
          result(false, "Input error: %s".format(message), "Input error: %s".format(message), r)
        case scalaz.Failure(UnexpectedErrorResponse(message)) =>
          result(false, "Unexpected error: %s".format(message), "Unexpected error: %s".format(message), r)
        case scalaz.Failure(EmptyErrorResponse(code)) =>
          result(false, "HTTP error code: %s".format(code), "HTTP error code: %s".format(code), r)
        case scalaz.Failure(FullErrorResponse(code, _)) =>
          result(false, "HTTP error code: %s".format(code), "HTTP error code: %s".format(code), r)
      }
    }
  }

  class ClientHasDeprovisionResponse(request: DeprovisionRequest) extends Matcher[ProvisioningClient] {
    override def apply[S <: ProvisioningClient](r: Expectable[S]): MatchResult[S] = {
      val client = r.value
      val response = client.deprovision(request).unsafePerformIO
      response match {
        case scalaz.Success(s) =>
          result(true, "Deprovision successful", "Deprovision successful", r)
        case scalaz.Failure(InputError(message)) =>
          result(false, "Input error: %s".format(message), "Input error: %s".format(message), r)
        case scalaz.Failure(UnexpectedErrorResponse(message)) =>
          result(false, "Unexpected error: %s".format(message), "Unexpected error: %s".format(message), r)
        case scalaz.Failure(EmptyErrorResponse(code)) =>
          result(false, "HTTP error code: %s".format(code), "HTTP error code: %s".format(code), r)
        case scalaz.Failure(FullErrorResponse(code, _)) =>
          result(false, "HTTP error code: %s".format(code), "HTTP error code: %s".format(code), r)
      }
    }
  }

  class ClientHasChangePlanResponse(request: ChangePlanRequest) extends Matcher[ProvisioningClient] {
    override def apply[S <: ProvisioningClient](r: Expectable[S]): MatchResult[S] = {
      val client = r.value
      val response = client.changePlan(request).unsafePerformIO
      response match {
        case scalaz.Success(s) =>
          result(true, "Change plan successful", "Change plan successful", r)
        case scalaz.Failure(InputError(message)) =>
          result(false, "Input error: %s".format(message), "Input error: %s".format(message), r)
        case scalaz.Failure(UnexpectedErrorResponse(message)) =>
          result(false, "Unexpected error: %s".format(message), "Unexpected error: %s".format(message), r)
        case scalaz.Failure(EmptyErrorResponse(code)) =>
          result(false, "HTTP error code: %s".format(code), "HTTP error code: %s".format(code), r)
        case scalaz.Failure(FullErrorResponse(code, _)) =>
          result(false, "HTTP error code: %s".format(code), "HTTP error code: %s".format(code), r)
      }
    }
  }

  class ClientHasProvisionError(request: ProvisionRequest, code: Int) extends Matcher[ProvisioningClient] {
    override def apply[S <: ProvisioningClient](r: Expectable[S]): MatchResult[S] = {
      val client = r.value
      val response = client.provision(request).unsafePerformIO
      response match {
        case scalaz.Success(s) =>
          result(false, "Expected a provision error but received success", "Expected a provision error but received success", r)
        case scalaz.Failure(InputError(message)) =>
          result(false, "Input error: %s".format(message), "Input error: %s".format(message), r)
        case scalaz.Failure(UnexpectedErrorResponse(message)) =>
          result(false, "Unexpected error: %s".format(message), "Unexpected error: %s".format(message), r)
        case scalaz.Failure(EmptyErrorResponse(c)) =>
          result(c == code, "Provision response has code: %s".format(c), "Provision response has code: %s, expected: %s".format(c, code), r)
        case scalaz.Failure(FullErrorResponse(c, _)) =>
          result(c == code, "Provision response has code: %s".format(c), "Provision response has code: %s, expected: %s".format(c, code), r)
      }
    }
  }

  class ClientHasDeprovisionError(request: DeprovisionRequest, code: Int) extends Matcher[ProvisioningClient] {
    override def apply[S <: ProvisioningClient](r: Expectable[S]): MatchResult[S] = {
      val client = r.value
      val response = client.deprovision(request).unsafePerformIO
      response match {
        case scalaz.Success(s) =>
          result(false, "Expected a deprovision error but received success", "Expected a deprovision error but received success", r)
        case scalaz.Failure(InputError(message)) =>
          result(false, "Input error: %s".format(message), "Input error: %s".format(message), r)
        case scalaz.Failure(UnexpectedErrorResponse(message)) =>
          result(false, "Unexpected error: %s".format(message), "Unexpected error: %s".format(message), r)
        case scalaz.Failure(EmptyErrorResponse(c)) =>
          result(c == code, "Deprovision response has code: %s".format(c), "Deprovision response has code: %s, expected: %s".format(c, code), r)
        case scalaz.Failure(FullErrorResponse(c, _)) =>
          result(c == code, "Deprovision response has code: %s".format(c), "Deprovision response has code: %s, expected: %s".format(c, code), r)
      }
    }
  }

  class ClientHasChangePlanError(request: ChangePlanRequest, code: Int) extends Matcher[ProvisioningClient] {
    override def apply[S <: ProvisioningClient](r: Expectable[S]): MatchResult[S] = {
      val client = r.value
      val response = client.changePlan(request).unsafePerformIO
      response match {
        case scalaz.Success(s) =>
          result(false, "Expected a change plan error but received success", "Expected a change plan error but received success", r)
        case scalaz.Failure(InputError(message)) =>
          result(false, "Input error: %s".format(message), "Input error: %s".format(message), r)
        case scalaz.Failure(UnexpectedErrorResponse(message)) =>
          result(false, "Unexpected error: %s".format(message), "Unexpected error: %s".format(message), r)
        case scalaz.Failure(EmptyErrorResponse(c)) =>
          result(c == code, "Change plan response has code: %s".format(c), "Change plan response has code: %s, expected: %s".format(c, code), r)
        case scalaz.Failure(FullErrorResponse(c, _)) =>
          result(c == code, "Change plan response has code: %s".format(c), "Change plan response has code: %s, expected: %s".format(c, code), r)
      }
    }
  }

}

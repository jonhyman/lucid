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

import org.apache.commons.lang.StringUtils
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.specs2.execute.{Result => SpecsResult}
import org.specs2.mock.Mockito
import org.specs2.specification._
import com.stackmob.lucid._

trait CommonContext extends Around with Mockito {

  override def around[T <% SpecsResult](t: => T): SpecsResult = t

  lazy val genNonEmptyAlphaStr: Gen[String] = Gen.alphaStr.suchThat(StringUtils.isNotBlank(_))

  lazy val genNonEmptyStr: Gen[String] = arbitrary[String].suchThat(StringUtils.isNotBlank(_))

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
      prefix <- genNonEmptyAlphaStr
      suffix <- genNonEmptyAlphaStr
    } yield prefix + "@" + suffix
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

}

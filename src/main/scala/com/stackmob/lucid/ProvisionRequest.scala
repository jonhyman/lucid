package com.stackmob.lucid

/**
 * Copyright 2012-2013 StackMob
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

import java.net.URI
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.scalaz.JsonScalaz._

case class SSORequest(id: String, token: String, email: String, path: URI, timestamp: Long)
case class ProvisionRequest(override val id: String, plan: String, email: String) extends BasicAuthRequest(id)
case class ChangePlanRequest(override val id: String, plan: String) extends BasicAuthRequest(id)
case class DeprovisionRequest(override val id: String) extends BasicAuthRequest(id)
abstract class BasicAuthRequest(val id: String)

case class ProvisionResponse(configVars: Map[String, String], override val location: URI) extends LocationResponse(location)
case class SSOResponse(override val location: URI) extends LocationResponse(location)
abstract class LocationResponse(val location: URI)
private[lucid] case class InternalProvisionResponse(configVars: Map[String, String])

class LucidException(message: String, throwable: Throwable = null) extends Exception(message, throwable)

object ProvisionRequest {

  private[lucid] val idJSONKey = "id"
  private[lucid] val planJSONKey = "plan"
  private[lucid] val emailJSONKey = "email"

  implicit val provisionRequestJSONW = new JSONW[ProvisionRequest] {
    override def write(request: ProvisionRequest): JValue = {
      (idJSONKey -> request.id) ~ (planJSONKey -> request.plan) ~ (emailJSONKey -> request.email)
    }
  }

}

object ChangePlanRequest {

  private[lucid] val planJSONKey = "plan"

  implicit val changePlanRequestJSONW = new JSONW[ChangePlanRequest] {
    override def write(request: ChangePlanRequest): JValue = {
      (planJSONKey -> request.plan)
    }
  }

}

private[lucid] object InternalProvisionResponse {

  val configVarsKey = "config-vars"

  implicit val internalProvisionResponseJSON = new JSON[InternalProvisionResponse] {
    override def read(json: JValue): Result[InternalProvisionResponse] = {
      field[Map[String, String]](configVarsKey)(json).map(InternalProvisionResponse(_))
    }
    override def write(value: InternalProvisionResponse): JValue = {
      (configVarsKey -> value.configVars)
    }
  }

}


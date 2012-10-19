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

import org.specs2._
import org.specs2.mock.Mockito
import scalaz._
import scalaz.effects._
import Scalaz._
import org.scalacheck.Gen
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._

class ProvisioningSpecs
  extends Specification
  with ScalaCheck { override def is =

  "Provisioning Specs".title                                                                                            ^
  """
  The provisioning specs verify the functionality of third party provisioning API's.
  """                                                                                                                   ^
                                                                                                                        p^
  "Provisioning should => POST /provision/stackmob"                                                                     ^
    "Return 201 ok if the provision was successful"                                                                     ! pending ^
    "Return 401 not authorized if authentication fails"                                                                 ! pending ^
    "Return 409 conflict if a plan exists for the given id"                                                             ! pending ^
    "Return 50x if there is an error handling the request"                                                              ! pending ^
                                                                                                                        endp^
  "Deprovisioning should => DELETE /provision/stackmob/:id"                                                             ^
    "Return 204 no content if the deprovision was successful"                                                           ! pending ^
    "Return 401 not authorized if authentication fails"                                                                 ! pending ^
    "Return 404 not found if no plan exists for the given id"                                                           ! pending ^
    "Return 50x if there is an error handling the request"                                                              ! pending ^
                                                                                                                        endp^
  "Changing a plan should => PUT /provision/stackmob/:id"                                                               ^
    "Return 204 no content if the plan was changed"                                                                     ! pending ^
    "Return 401 not authorized if authentication fails"                                                                 ! pending ^
    "Return 404 not found if no plan exists for the given id"                                                           ! pending ^
    "Return 50x if there is an error handling the request"                                                              ! pending ^
                                                                                                                        end
}

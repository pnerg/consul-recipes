/**
  *  Copyright 2020 Peter Nerg
  *
  *  Licensed under the Apache License, Version 2.0 (the "License");
  *  you may not use this file except in compliance with the License.
  *  You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */
package org.dmonix.consul

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.dmonix.consul.ConsulJsonProtocol._
import org.dmonix.consul.Implicits._
import spray.json._

/**
  * @author Peter Nerg
  */
class ConsulSimSessionSpec extends ConsulSpecification with Specs2RouteTest {

  private val sim = ConsulSim()
  private val storage = sim.sessionStorage

  "Creating session shall be successful" >> {
    Put("/v1/session/create") ~> sim.sessionRoute ~> check {
      status shouldEqual StatusCodes.OK
      val id = responseAs[String].parseJson.fieldValOrFail[String]("ID")
      storage.assertSessionExists(id)
    }
  }

  "Destroying session shall" >> {
    "be successful for non existing session" >> {
      Put("/v1/session/destroy/no-such-id") ~> sim.sessionRoute ~> check {
        status shouldEqual StatusCodes.OK
        storage.assertSessionNotExists("no-such-id")
      }
    }
    "be successful for existing session" >> {
      val id = storage.createSession()
      Put("/v1/session/destroy/" + id) ~> sim.sessionRoute ~> check {
        status shouldEqual StatusCodes.OK
        storage.assertSessionNotExists(id)
      }
    }
  }
}

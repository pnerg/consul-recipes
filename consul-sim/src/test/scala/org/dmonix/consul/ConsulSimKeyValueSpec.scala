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
import spray.json._

/**
      * @author Peter Nerg
      */
class ConsulSimKeyValueSpec extends ConsulSpecification with Specs2RouteTest {

  private val sim = ConsulSim()
  private val storage = sim.keyValueStorage

  private implicit class PimpedString(string: String) {
    def parseAsKeyValues: Stream[KeyValue] = string.parseJson match {
      case JsArray(data) => data.toStream.map(_.convertTo[KeyValue])
      case _             => deserializationError(s"Got unexpected response for [$string]")
    }
  }

  "creating a key shall" >> {
    "be successful with no value if the key did not exist" >> {
      val key = "foo/bar"
      Put("/v1/kv/" + key) ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] === "true"
        storage.assertKeyValue(key, None)
      }
    }
    "be successful with no value using 'Flags' if the key did not exist" >> {
      val key = "foo/bar2"
      Put("/v1/kv/" + key + "?flags=6969") ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] === "true"
        storage.assertKeyValue(key, None)
        storage.getKeyValue(key) must beSome().which(_.flags === 6969)
      }
    }
    "be successful with value if the key did not exist" >> {
      val key = "foo/bar3"
      val value = Some("my-value")
      Put("/v1/kv/" + key, value) ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] === "true"
        storage.assertKeyValue(key, value)
      }
    }
  }

  "fetching a key shall" >> {
    "return 404 for a non existing key" >> {
      Get("/v1/kv/no-such-key") ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    "return 200 for an existing key" >> {
      val kv = storage.createInitialKey()
      Get("/v1/kv/" + kv.key) ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String].parseAsKeyValues.head.value === kv.value
      }
    }
  }

  "deleting a key shall" >> {
    "return 200 for non existing key" >> {
      Delete("/v1/kv/no-such-key") ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "return 200 for an existing key" >> {
      val kv = storage.createInitialKey()
      Delete("/v1/kv/" + kv.key) ~> sim.keyValueRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

}

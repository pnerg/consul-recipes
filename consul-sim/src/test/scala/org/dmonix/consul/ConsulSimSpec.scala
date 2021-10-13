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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
  * Testing the HTTP interface for the [[ConsulSim]]
  *
  * @author Peter Nerg
  */
class ConsulSimSpec extends Specification with BeforeAfterAll {

  private implicit val system = ActorSystem("ConsulSimSpec")

  private var url: String = null

  implicit class PimpedFuture[T](f: Future[T]) {

    /**
      * Blocks and waits for the Future to complete then returning the result (Try) of the execution.
      * The resulting Try will be failed in case the Future failed.
      * @return The resulting Try from the Future once it has finished.
      */
    def waitForResult: T = {
      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent.duration.DurationInt
      val secureFuture = f
        .map(Success(_))
        .recover({ case ex: Throwable => Failure(ex) })
      Await.result(secureFuture, 1.seconds) get
    }
  }

  private val consulSim = ConsulSim()

  override def beforeAll = url = "http://127.0.0.1:" + consulSim.start().port + "/v1"
  override def afterAll = {
    consulSim.shutdown()
    system.terminate().waitForResult
  }

  private def consulHost: ConsulHost = consulSim.consulHost.get

  "Managing key values" >> {
    "shall return 404 for non existing key" >> {
      val response = singleRequest(kvURL("no-such"))
      response.status shouldEqual StatusCodes.NotFound
    }
    "shall return 200 for existing key" >> {
      consulSim.keyValueStorage.createOrUpdate("a-key", None)
      val response = singleRequest(kvURL("a-key"))
      response.status shouldEqual StatusCodes.OK
    }
  }

  private def kvURL(key: String) = url + "/kv/" + key

  private def singleRequest(url: String): HttpResponse = Http().singleRequest(HttpRequest(uri = url)).waitForResult
}

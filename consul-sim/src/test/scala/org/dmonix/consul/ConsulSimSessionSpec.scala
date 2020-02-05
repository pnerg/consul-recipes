package org.dmonix.consul

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2.mutable.Specification


/**
  * @author Peter Nerg
  */
class ConsulSimSessionSpec extends Specification with Specs2RouteTest {

  private val sim = ConsulSim()
  
  "Creating session shall be successful" >> {
    Put("/v1/session/create") ~> sim.sessionRoute ~> check {
      status shouldEqual StatusCodes.OK
    } 
  }

  "Destroying session" >> {
    "shall be successful for non existing session" >> {
      Put("/v1/session/destroy/no-such-id") ~> sim.sessionRoute ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}

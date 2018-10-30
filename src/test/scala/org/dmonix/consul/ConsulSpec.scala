package org.dmonix.consul

import java.util.UUID

import org.specs2.mutable.Specification

import scala.util.Success
import spray.json._
import ConsulJsonProtocol._

/**
  * @author Peter Nerg
  */
class ConsulSpec extends Specification with MockHttpSender with MockResponses {

  "Creating a session" >> {
    "shall be successful if a valid response is provided" >> {
      val sender = mockPut {
        case ("/session/create", None) => sessionCreatedResponse("12345")
      }
      
      val consul = new Consul(sender)
      consul.createSession(Session(name=Option("test"))) must beASuccessfulTry
    }
    
    "shall be a failure if a non-valid response is provided" >> {

      val sender = mockPut {
        case ("/session/create", None) => failureResponse
      }

      val consul = new Consul(sender)
      consul.createSession(Session(name=Option("test"))) must beAFailedTry
    }
  }

  "Destroying a session" >> {
    val sessionID = UUID.randomUUID().toString
    val sessionPath = "/session/destroy/"+sessionID
    "shall be successful if a valid response is provided" >> {
      val sender = mockPut {
        case (`sessionPath`, None) => Success("true")
      }

      val consul = new Consul(sender)
      consul.destroySession(sessionID) must beASuccessfulTry
    }

    "shall be a failure if a non-valid response is provided" >> {
      val sender = mockPut {
        case (`sessionPath`, None) => failureResponse
      }

      val consul = new Consul(sender)
      consul.destroySession(sessionID) must beAFailedTry
    }
  }

  "Renewing a session" >> {
    val sessionID = UUID.randomUUID().toString
    val sessionPath = "/session/renew/"+sessionID
    "shall be successful if a valid response is provided" >> {
      val sender = mockPut {
        case (`sessionPath`, None) => sessionResponse(Session())
      }

      val consul = new Consul(sender)
      consul.renewSession(sessionID) must beASuccessfulTry
    }

    "shall be a failure if a non-valid response is provided" >> {
      val sender = mockPut {
        case (`sessionPath`, None) => failureResponse
      }

      val consul = new Consul(sender)
      consul.renewSession(sessionID) must beAFailedTry
    }
  }
}

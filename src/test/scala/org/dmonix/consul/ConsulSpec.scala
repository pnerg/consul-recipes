package org.dmonix.consul

import java.util.UUID

import org.specs2.mutable.Specification

import scala.util.Success

/**
  * Tests for the class ''Consul''
  * @author Peter Nerg
  */
class ConsulSpec extends Specification with MockHttpSender with MockResponses {

  "Creating a session" >> {
    "shall be successful if a valid response is provided" >> {
      val sender = mockPut {
        case ("/session/create", _) => sessionCreatedResponse("12345")
      }
      
      val consul = new Consul(sender)
      consul.createSession(Session(name=Option("test"))) must beASuccessfulTry("12345")
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
  
  "Store key/value" >> {
    val key = "/foo/bar/my-key"
    val data = "Some data"
    val path = "/kv/"+key
    "shall be successful for no data if valid response is provided" >> {
      val sender = mockPut{
        case (`path`, None) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(key, None) must beASuccessfulTry.withValue(true)
    }

    "shall be successful with data if valid response is provided" >> {
      val sender = mockPut{
        case (`path`, Some(`data`)) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(key, Some(data)) must beASuccessfulTry.withValue(true)
    }

    "shall be failure for no data if a non-valid response is provided" >> {
      val sender = mockPut{
        case (`path`, None) => failureResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(key, None) must beAFailedTry
    }

    "shall be failure with data if a non-valid response is provided" >> {
      val sender = mockPut{
        case (`path`, Some(`data`)) => failureResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(key, Some(data)) must beAFailedTry
    }
  }

  "Store key value using SetKeyValue" >> {
    val data = "Some data"
    val setKeyNoData = SetKeyValue(key = "/foo/bar/my-key", value = None)
    val setKeyWithData = setKeyNoData.copy(value = Some(data))
    val path = "/kv/"+setKeyNoData.key
    "shall be successful for no data if valid response is provided" >> {
      val sender = mockPut{
        case (`path`, None) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyNoData) must beASuccessfulTry.withValue(true)
    }

    "shall be successful with data if valid response is provided" >> {
      val sender = mockPut{
        case (`path`, Some(`data`)) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData) must beASuccessfulTry.withValue(true)
    }

    "shall be successful with 'cas' if valid response is provided" >> {
      val p = path + "?cas=69"
      val sender = mockPut{
        case (`p`, Some(`data`)) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData.copy(compareAndSet = Some(69))) must beASuccessfulTry.withValue(true)
    }

    "shall be successful with 'acquire' if valid response is provided" >> {
      val p = path + "?acquire=sessionID"
      val sender = mockPut{
        case (`p`, Some(`data`)) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData.copy(acquire = Some("sessionID"))) must beASuccessfulTry.withValue(true)
    }

    "shall be successful with 'acquire' if valid response is provided but the lock could not be taken" >> {
      val p = path + "?acquire=sessionID"
      val sender = mockPut{
        case (`p`, Some(`data`)) => falseResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData.copy(acquire = Some("sessionID"))) must beASuccessfulTry.withValue(false)
    }

    "shall be successful with 'release' if valid response is provided" >> {
      val p = path + "?release=sessionID"
      val sender = mockPut{
        case (`p`, Some(`data`)) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData.copy(release = Some("sessionID"))) must beASuccessfulTry.withValue(true)
    }

    "shall be successful with combining 'cas' and 'acquire' if valid response is provided" >> {
      val p = path + "?cas=69&acquire=sessionID"
      val sender = mockPut{
        case (`p`, Some(`data`)) => trueResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData.copy(compareAndSet = Some(69), acquire = Some("sessionID"))) must beASuccessfulTry.withValue(true)
    }

    "shall be failure for no data if a non-valid response is provided" >> {
      val sender = mockPut{
        case (`path`, None) => failureResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyNoData) must beAFailedTry
    }

    "shall be failure with data if a non-valid response is provided" >> {
      val sender = mockPut{
        case (`path`, Some(`data`)) => failureResponse
      }

      val consul = new Consul(sender)
      consul.storeKeyValue(setKeyWithData) must beAFailedTry
    }
  }
  
  /*
  "Read key/value" >> {
    val key = "foo/bar/my-key"
    val data = "Some data"
    val path = s"/kv/+$key?index=0&wait=0s"

    "shall be successful if a valid response is provided and contain data if the key existed" >> {  
      val sender = mockGet{
        case `path` => getKVResponse(Some(data))
        case "/kv/foo/bar/my-key?index=0&wait=0s" => getKVResponse(Some(data))
        case p => 
          println(p)
          failureResponse
      }
      val consul = new Consul(sender)
      consul.readKeyValue(key) must beSuccessfulTry.withValue(Some(data))      
    }
  }
  */

}

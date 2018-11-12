package org.dmonix.consul

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

/**
  * @author Peter Nerg
  */
class ConsulWithSimSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()

  override def beforeAll = consulSim.start()
  override def afterAll = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get
  
  "Session management" >> {
    "shall be successful creating a session" >> {
      val consul = Consul(consulHost)
      consul.createSession(Session(name=Option("test"))) must beASuccessfulTry
    }
  }
  
  "Key/values" >> {
    "shall be successful setting a key without value" >> {
      val consul = Consul(consulHost)
      consul.storeKeyValue("a-key", None) must beASuccessfulTry(true)
    }
    "shall be successful setting a key with value" >> {
      val consul = Consul(consulHost)
      consul.storeKeyValue("another-key", Some("a-value")) must beASuccessfulTry(true)
    }
    "shall return None for non-existing key" >> {
      val consul = Consul(consulHost)
      consul.readKeyValue("no-such-key") must beASuccessfulTry(None)
    }
  }
}

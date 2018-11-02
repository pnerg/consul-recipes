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
  private def httpSender():HttpSender = new ConsulHttpSender(consulHost)
  
  "Session management" >> {
    "shall be successful creating a session" >> {
      val consul = new Consul(httpSender())
      consul.createSession(Session(name=Option("test"))) must beASuccessfulTry
    }
  }
}

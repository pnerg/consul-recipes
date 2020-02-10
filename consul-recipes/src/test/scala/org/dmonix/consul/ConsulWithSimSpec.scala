/**
  *  Copyright 2018 Peter Nerg
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

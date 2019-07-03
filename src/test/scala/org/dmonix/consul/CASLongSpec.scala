/**
  *  Copyright 2019 Peter Nerg
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

import java.util.concurrent.atomic.AtomicInteger

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

/**
  * @author Peter Nerg
  */
class CASLongSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()
  private val atomic = new AtomicInteger(0)

  override def beforeAll = consulSim.start()
  override def afterAll = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get
  
  private def initLong(initVal:Long = 0):CASLong = CASLong.initiate(consulHost, "long-"+atomic.incrementAndGet(), initVal).get
  
  "performing init" >> {
    "key must have init value when created first time" >> {
      val counter = initLong(69)
      counter.currentValue() must beASuccessfulTry.withValue(69L)
    }
  }

}

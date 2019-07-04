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
  private lazy val consul = Consul(consulHost)
  
  private def initLong(initVal:Long = 0):CASLong = CASLong.initiate(consulHost, "long-"+atomic.incrementAndGet(), initVal).get
  
  "performing init" >> {
    "key must have init value when created first time" >> {
      val counter = initLong(69)
      counter.currentValue() must beASuccessfulTry.withValue(69L)
    }
  }

  "performing increment" >> {
    "increment shall return the new value of the long in case successful" >> {
      val counter = initLong(0)
      counter.incrementAndGet() must beASuccessfulTry.withValue(1L)
      counter.currentValue() must beASuccessfulTry.withValue(1L)
    }
    "increment with provided delta shall return the new value of the long in case successful" >> {
      val counter = initLong(0)
      counter.incrementAndGet(69) must beASuccessfulTry.withValue(69L)
      counter.currentValue() must beASuccessfulTry.withValue(69L)
    }
  }

  "performing decrement" >> {
    "decrement shall return the new value of the long in case successful" >> {
      val counter = initLong(70)
      counter.decrementAndGet() must beASuccessfulTry.withValue(69L)
      counter.currentValue() must beASuccessfulTry.withValue(69L)
    }
    "decrement with provided delta shall return the new value of the long in case successful" >> {
      val counter = initLong(69)
      counter.decrementAndGet(69) must beASuccessfulTry.withValue(0L)
      counter.currentValue() must beASuccessfulTry.withValue(0L)
    }
    "decrement beyond zero shall return the new negative value of the long in case successful" >> {
      val counter = initLong(0)
      counter.decrementAndGet() must beASuccessfulTry.withValue(-1L)
      counter.currentValue() must beASuccessfulTry.withValue(-1L)
    }
  }
  
  "get current value" >> {
    "shall be successful if key exists and has a numerical value" >> {
      val counter = initLong(69)
      counter.currentValue() must beASuccessfulTry.withValue(69L)
    }
    "shall fail if key doesn't exists" >> {
      val counter = new CASLong(consulHost, "no-such-path")
      counter.currentValue() must beAFailedTry.withThrowable[NoSuchKeyException]
    }
    "shall fail if exists but has no value" >> {
      val path = "empty-path"
      consul.storeKeyValue(path, None)
      
      val counter = new CASLong(consulHost, path)
      println(counter.currentValue())
      counter.currentValue() must beAFailedTry.withThrowable[NumberFormatException]
    }
    "shall fail if exists but has non-numerical value" >> {
      val path = "not-a-number-path"
      consul.storeKeyValue(path, Some("not-a-number"))

      val counter = new CASLong(consulHost, path)
      println(counter.currentValue())
      counter.currentValue() must beAFailedTry.withThrowable[NumberFormatException]
    }
  }
}

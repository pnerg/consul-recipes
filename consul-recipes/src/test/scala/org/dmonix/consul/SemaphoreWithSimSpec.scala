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
import scala.concurrent.duration.DurationInt

/**
  * Tests for [[Semaphore]] 
  * @author Peter Nerg
  */
class SemaphoreWithSimSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()

  override def beforeAll = consulSim.start()
  override def afterAll = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get

  "Single member semaphore" >> {
    "Shall fail to create instance if illegal initialPermit is supplied" >> {
      Semaphore(consulHost, "illegal-value", 0) must beFailedTry
    }
    "shall acquire successfully if there are permits" >> {
      val semaphore = Semaphore(consulHost, "single-member-with-permits", 1).get
      semaphore.tryAcquire() must beASuccessfulTry(true)
      semaphore.tryAcquire() must beASuccessfulTry(true) //second acquire shall immediately return true as we already hold a permit
      semaphore.release() must beASuccessfulTry(true)
    }
    "shall acquire successfully with wait if there are permits" >> {
      val semaphore = Semaphore(consulHost, "single-member-with-permits-with-wait", 1).get
      semaphore.tryAcquire(5.seconds) must beASuccessfulTry(true)
      semaphore.tryAcquire(5.seconds) must beASuccessfulTry(true) //second acquire shall immediately return true as we already hold a permit
      semaphore.release() must beASuccessfulTry(true)
    }
  }

  "multi member semaphore" >> {
    val name = "multi-member"
    val permits = 1
    val s1 = Semaphore(consulHost, name, permits).get
    val s2 = Semaphore(consulHost, name, permits).get
    val s3 = Semaphore(consulHost, name, permits).get
    
    //only s1 should successfully acquire
    s1.tryAcquire() must beASuccessfulTry(true)
    s2.tryAcquire() must beASuccessfulTry(false)
    s3.tryAcquire() must beASuccessfulTry(false)
    
    //release s1 and s2 should successfully acquire
    s1.release() must beASuccessfulTry(true)
    s2.tryAcquire() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(false)

    //release s2 and s3 should successfully acquire
    s2.release() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(true)

    s3.release() must beASuccessfulTry(true)
  }

  "multi member and permit semaphore" >> {
    val name = "multi-permit"
    val permits = 2
    val s1 = Semaphore(consulHost, name, permits).get
    val s2 = Semaphore(consulHost, name, permits).get
    val s3 = Semaphore(consulHost, name, permits).get

    //both s1 and s2 should successfully acquire
    s1.tryAcquire() must beASuccessfulTry(true)
    s2.tryAcquire() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(false)

    //release s1 and s3 should successfully acquire (s2 should still be ok)
    s1.release() must beASuccessfulTry(true)
    s2.tryAcquire() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(true)

    //release s2 and s3 should still be ok
    s2.release() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(true)

    s3.release() must beASuccessfulTry(true)
  }
}

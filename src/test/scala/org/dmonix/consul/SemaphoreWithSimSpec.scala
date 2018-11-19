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
  override def afterAll = ()//consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get
//    private def consulHost:ConsulHost = ConsulHost("localhost", 8500)

  /*
  "Single member semaphore" >> {
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
    "shall fail acquire if there are no permits" >> {
      val semaphore = Semaphore(consulHost, "single-member-without-permits", 0).get
      semaphore.tryAcquire() must beASuccessfulTry(false)
      semaphore.release() must beASuccessfulTry(false)
    }
    "shall fail acquire with wait if there are no permits" >> {
      val semaphore = Semaphore(consulHost, "single-member-without-permits-with-wait", 0).get
      semaphore.tryAcquire(20.millis) must beASuccessfulTry(false)
      semaphore.release() must beASuccessfulTry(false)
    }
  }
*/
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

    //both s1 & s2 should successfully acquire
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

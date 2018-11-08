package org.dmonix.consul

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import scala.concurrent.duration.DurationInt

/**
  * @author Peter Nerg
  */
class SemaphoreWithSimSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()

  override def beforeAll = consulSim.start()
  override def afterAll = ()//consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get
//    private def consulHost:ConsulHost =ConsulHost("localhost", 8500)

  
  "Single member semaphore" >> {
    "shall acquire successfully if there are permits" >> {
      val semaphore = Semaphore(consulHost, "single-member-with-permits", 1).get
      semaphore.tryAcquire() must beASuccessfulTry(true)
      semaphore.tryAcquire() must beASuccessfulTry(true) //second acquire shall immediately return true as we already hold a permit
      semaphore.release() must beASuccessfulTry(true)
    }
    "shall acquire successfully with wait if there are permits" >> {
      val semaphore = Semaphore(consulHost, "single-member-with-permits-2", 1).get
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
      val semaphore = Semaphore(consulHost, "single-member-without-permits-2", 0).get
      semaphore.tryAcquire(20.millis) must beASuccessfulTry(false)
      semaphore.release() must beASuccessfulTry(false)
    }
  }

  "multi member semaphore" >> {
    val s1 = Semaphore(consulHost, "multi-member", 1).get
    val s2 = Semaphore(consulHost, "multi-member", 1).get
    val s3 = Semaphore(consulHost, "multi-member", 1).get
    
    s1.tryAcquire() must beASuccessfulTry(true)
    s2.tryAcquire() must beASuccessfulTry(false)
    s3.tryAcquire() must beASuccessfulTry(false)
    
    s1.release() must beASuccessfulTry(true)
    s2.tryAcquire() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(false)

    s2.release() must beASuccessfulTry(true)
    s3.tryAcquire() must beASuccessfulTry(true)

    s3.release() must beASuccessfulTry(true)
  }
}

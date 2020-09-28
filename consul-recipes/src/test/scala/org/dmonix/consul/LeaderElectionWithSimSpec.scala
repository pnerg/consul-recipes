package org.dmonix.consul

import java.util.concurrent.TimeUnit
import scala.collection._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

/**
  * @author Peter Nerg
  */
class LeaderElectionWithSimSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()
  private var candidates = Seq[Candidate]()
  override def beforeAll = {
    candidates.foreach(_.leave())
    consulSim.start()
  }
  override def afterAll = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get

  "Single member election" >> {
    val observer = new TestObserver()
    val candidate = joinCandidate("single-member", observer)
    candidate.isLeader === true
    observer.isElected === true
    candidate.leave()
    ok
  }

  "Multi member election" >> {
    val groupName = "multi-group"
    val observer1 = new TestObserver()
    val observer2 = new TestObserver()
    lazy val candidate1 = joinCandidate(groupName, observer1)
    lazy val candidate2 = joinCandidate(groupName, observer2)

    observer1.blockForChange()
    candidate1.isLeader === true
    observer1.isElected === true

    candidate2.isLeader === false
    observer2.isElected === false
    
    //drop the leader, force a re-election
    candidate1.leave()

    observer1.blockForChange()
    candidate1.isLeader === false
    observer1.isElected === false

    observer2.blockForChange()
    candidate2.isLeader === true
    observer2.isElected === true
    
    candidate2.leave()
    ok
  }
  
  private def joinCandidate(groupName:String, observer: ElectionObserver):Candidate = {
    val candidate = LeaderElection.joinLeaderElection(consulHost, groupName, None, Some(observer)).get
    candidates = candidates :+ candidate
    candidate
  }

  private class TestObserver extends ElectionObserver {
    private var blocker = new java.util.concurrent.Semaphore(0)
    @volatile var isElected = false
    def blockForChange():Unit = {
      blocker.tryAcquire(10, TimeUnit.SECONDS)
      blocker = new java.util.concurrent.Semaphore(0)
    }
   
    private def elected(state:Boolean): Unit = {
      isElected = state
      blocker.release()
    }
    
    override def elected():  Unit = elected(true)
    override def unElected(): Unit = elected(false)
  }
}

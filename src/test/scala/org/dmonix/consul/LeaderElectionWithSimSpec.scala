package org.dmonix.consul

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

/**
  * @author Peter Nerg
  */
class LeaderElectionWithSimSpec extends Specification with BeforeAfterAll {
  private val consulSim = ConsulSim()

  override def beforeAll = consulSim.start()
  override def afterAll = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get
  //private def consulHost:ConsulHost =ConsulHost("localhost", 8500)

  "Single member election" >> {
    val observer = new TestObserver()
    lazy val candidate = LeaderElection.joinLeaderElection(consulHost, "single-member", None, Some(observer)).get
    candidate.isLeader === true
    observer.isElected === true
    candidate.leave()
    ok
  }

  "Multi member election" >> {
    val groupName = "multi-group"
    val observer1 = new TestObserver()
    val observer2 = new TestObserver()
    lazy val candidate1 = LeaderElection.joinLeaderElection(consulHost, groupName, None, Some(observer1)).get
    lazy val candidate2 = LeaderElection.joinLeaderElection(consulHost, groupName, None, Some(observer2)).get
    
    
    candidate1.isLeader === true
    observer1.isElected === true

    candidate2.isLeader === false
    observer2.isElected === false
    
    //drop the leader, force a re-election
    candidate1.leave()

    candidate1.isLeader === false
    observer1.isElected === false

    //TODO ugly sleep, fix with a blocking function on the observer
    Thread.sleep(500)
    
    candidate2.isLeader === true
    observer2.isElected === true
    candidate2.leave()
    ok
  }

  private class TestObserver extends ElectionObserver {
    @volatile var isElected = false
    override def elected(): Unit = isElected = true
    override def unElected(): Unit = isElected = false
  }
}

package org.dmonix.consul

import org.specs2.matcher.EventuallyMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll

/**
  * Tests for the ''LeaderElection''
  * @author Peter Nerg
  */
class LeaderElectionWithSimSpec extends Specification with BeforeAfterAll with EventuallyMatchers {
  private val consulSim = ConsulSim()
  override def beforeAll():Unit = consulSim.start()
  override def afterAll():Unit = consulSim.shutdown()

  private def consulHost:ConsulHost = consulSim.consulHost.get

  "Single member election" >> {
    val observer = new TestObserver()
    val candidate = joinCandidate("single-member", observer)
    try {
      candidate.isLeader must beTrue.eventually
      observer.isElected must beTrue.eventually
      candidate.leave()
      ok
    } finally {
      candidate.leave()
    }
  }

  "Multi member election" >> {
    val groupName = "multi-group"
    val observer1 = new TestObserver()
    val observer2 = new TestObserver()
    lazy val candidate1 = joinCandidate(groupName, observer1)
    lazy val candidate2 = joinCandidate(groupName, observer2)
    try {
      //candidate 1 joined first and should be elected
      //candidate 2 should NOT be elected
      candidate1.isLeader must beTrue.eventually
      observer1.isElected must beTrue.eventually
      candidate2.isLeader must beFalse.eventually
      observer2.isElected must beFalse.eventually

      //drop the leader, force a re-election
      candidate1.leave()

      //now candidate 1 should have released leadership
      //and candidate 2 should have become leader
      candidate1.isLeader must beFalse.eventually
      observer1.isElected must beFalse.eventually
      candidate2.isLeader must beTrue.eventually
      observer2.isElected must beTrue.eventually
    } finally {
      candidate1.leave()
      candidate2.leave()
    }
  }
  
  private def joinCandidate(groupName:String, observer: ElectionObserver):Candidate = LeaderElection.joinLeaderElection(consulHost, groupName, None, Some(observer)).get

  private class TestObserver extends ElectionObserver {
    @volatile var isElected = false
    override def elected():  Unit = isElected = true
    override def unElected(): Unit = isElected = false
  }
}

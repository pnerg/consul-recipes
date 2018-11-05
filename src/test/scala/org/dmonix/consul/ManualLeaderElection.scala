package org.dmonix.consul

/**
  * Simple app to illustrate the leadership election process.   
  * Start multiple instances to have more candidates for the election process.  
  * Requires Consul to be listening to localhost:8500 (see README.md for example to start Consul)
  * @author Peter Nerg
  */
object ManualLeaderElection extends App {
  private val semaphore = new java.util.concurrent.Semaphore(0)
  
  //simple observer that just prints election changes
  private val observer = new ElectionObserver {
    override def elected(): Unit = println("Elected")
    override def unElected(): Unit = println("UnElected")
  }
  
  private val candidate = LeaderElection.joinLeaderElection(ConsulHost("localhost"), "example-group", None, Option(observer)) get
  
  //catches shutdown of the app and makes the candidate leave the election process
  sys.addShutdownHook {
    candidate.leave()
    semaphore.release()
  }
  
  //hold here for the lifetime of the app
  semaphore.acquire()
}

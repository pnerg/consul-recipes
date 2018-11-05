package org.dmonix.consul

/**
  * Simple app to illustrate the usage of a ''Semaphore''.   
  * Start multiple instances to have competing users to the Semaphore.  
  * Requires Consul to be listening to localhost:8500 (see README.md for example to start Consul)
  * @author Peter Nerg
  */
object ManualSemaphore extends App {
  private val blocker = new java.util.concurrent.Semaphore(0)

  val semaphore = Semaphore(ConsulHost("localhost"), "example-semaphore", 1).get

  println(semaphore.tryAcquire())
  
  //catches shutdown of the app and makes the candidate leave the election process
  sys.addShutdownHook {
    semaphore.release()
    blocker.release()
  }

  //hold here for the lifetime of the app
  blocker.acquire()

}

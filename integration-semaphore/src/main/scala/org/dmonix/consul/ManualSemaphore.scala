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

import scala.concurrent.duration.DurationInt
import scala.util._

/**
  * Simple app to illustrate the usage of a ''Semaphore''.   
  * Start multiple instances to have competing users to the Semaphore.  
  * Requires Consul to be listening to localhost:8500 (see README.md for example to start Consul)
  * @author Peter Nerg
  */
object ManualSemaphore extends App {
  private val blocker = new java.util.concurrent.Semaphore(0)
  private val semaphore = Semaphore(ConsulHost("localhost"), "example-semaphore", 1).get

  //catches shutdown of the app and releases any potential permits
  sys.addShutdownHook {
    //try this to force a destruction of the semaphore, will fail all those blocking/waiting for a permit
    //semaphore.destroy()
    semaphore.release()
    blocker.release()
  }

  semaphore.tryAcquire(5.minutes) match {
    case Success(true)  => println("Yay, got a permit!")
    case Success(false) => println("Didn't get a permit...:(")
    case Failure(SemaphoreDestroyed(name)) =>
      println(s"Semaphore [$name] was destroyed, just keep doing something else")
    case Failure(ex) => ex.printStackTrace()
  }

  //hold here for the lifetime of the app
  blocker.acquire()

}

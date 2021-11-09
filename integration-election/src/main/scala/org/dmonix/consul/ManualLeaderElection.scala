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

/**
  * Simple app to illustrate the leadership election process.   
  * Start multiple instances to have more candidates for the election process.  
  * Requires Consul to be listening to localhost:8500 (see README.md for example to start Consul)
  * @author Peter Nerg
  */
object ManualLeaderElection extends App {
  private val blocker = new java.util.concurrent.Semaphore(0)

  // simple observer that just prints election changes
  private val observer = new ElectionObserver {
    override def elected(): Unit = println("Elected")
    override def unElected(): Unit = println("UnElected")
  }

  private val candidate =
    LeaderElection.joinLeaderElection(ConsulHost("localhost"), "example-group", None, Option(observer)) get

  // catches shutdown of the app and makes the candidate leave the election process
  sys.addShutdownHook {
    candidate.leave()
    blocker.release()
  }

  // hold here for the lifetime of the app
  blocker.acquire()
}

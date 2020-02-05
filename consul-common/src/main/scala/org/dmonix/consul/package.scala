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
package org.dmonix

import scala.concurrent.duration.FiniteDuration

/**
  * @author Peter Nerg
  */
package object consul {

  type SessionID = String

  /**
    * Indicates that the ''.destroy'' method has been invoked on semaphore
    * @param name The name of the semaphore
    */
  case class SemaphoreDestroyed(name:String) extends Exception(s"The semaphore [$name] has been permanently destroyed")
  
  /**
    * Connection information to a Consul instance
    * @param host The host
    * @param port Optional port (default 8500)
    */
  case class ConsulHost(host:String, port:Int = 8500)

  /**
    * Represents stored session data in Consul
    * @param name Optional name of the session
    * @param lockDelay Optional lock delay
    * @param node Optional Consul node for the session
    * @param behavior Optional ''behavior'' of the session
    * @param ttl Optional Time-To-Live of the session, not providing this will in practice be a session that never times out 
    */
  case class Session(name:Option[String] = None, lockDelay:Option[FiniteDuration] = None, node:Option[String] = None, behavior:Option[String] = None, ttl:Option[FiniteDuration] = None)

  /**
    * Represents a key/value stored in Consul
    * @param createIndex The ''CreateIndex'' value as stored in Consul
    * @param modifyIndex The ''ModifyIndex'' value as stored in Consul
    * @param lockIndex The ''LockIndex'' value as stored in Consul
    * @param key The name/path of the key
    * @param value The value in plain string format already Base64 decoded
    * @param session Optional owner (session ÍD) of the key
    */
  case class KeyValue(createIndex:Int, modifyIndex:Int, lockIndex:Int, key:String, value:Option[String], session:Option[String])

  /**
    * Data for setting a key/value
    * @param key The name/path of the key (e.g. foo/bar/my-data)
    * @param modifyIndex Optional modification index value to block on
    * @param maxWait Optional max wait time, used in conjunction with ''modifyIndex''
    */
  case class GetKeyValue(key:String, modifyIndex:Option[Int] = None, maxWait:Option[FiniteDuration] = None, recursive:Boolean = false)
  
  /**
    * Data for setting a key/value
    * @param key The name/path of the key (e.g. foo/bar/my-data)
    * @param value Optional value of the key/data
    * @param compareAndSet Will only write the key/value of this value matches the ''ModifyIndex'' of the key stored in Consul 
    * @param acquire Attempts to take a lock on the key using the provided session ID
    * @param release Attempts to release a lock on the key using the provided session ID
    */
  case class SetKeyValue(key:String, value:Option[String] = None, compareAndSet:Option[Int] = None, acquire:Option[SessionID] = None, release:Option[SessionID] = None)

  /**
    * Data for deleting a key/value
    * @param key The name/path of the key (e.g. foo/bar/my-data)
    * @param compareAndSet Will only delete the key/value of this value matches the ''ModifyIndex'' of the key stored in Consul 
    * @param recursive If all key/values in the provided path shall be deleted
    */
  case class DeleteKeyValue(key:String, compareAndSet:Option[Int] = None, recursive:Boolean = false)

  /**
    * Represents the ''.semaphore'' file stored for each semaphore instance in Consul.
    * @param permits The initial permits as stored in Consul
    * @param holders The sessionID's for all holders of a permission
    */
  private[consul] case class SemaphoreData(permits:Int, holders:Set[SessionID]) {
    def addHolder(sessionID: SessionID):SemaphoreData = copy(holders = holders + sessionID)
    def removeHolder(sessionID: SessionID): SemaphoreData = copy(holders = holders.filterNot(_ == sessionID))
    def hasPermits():Boolean = (permits - holders.size) > 0
    def isHolder(sessionID: SessionID):Boolean = holders.contains(sessionID)
  }
}
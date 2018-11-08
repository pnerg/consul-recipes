package org.dmonix

import scala.concurrent.duration.FiniteDuration

/**
  * @author Peter Nerg
  */
package object consul {

  type SessionID = String

  case class ConsulHost(host:String, port:Int = 8500)
  
  case class Session(name:Option[String] = None, lockDelay:Option[FiniteDuration] = None, node:Option[String] = None, behavior:Option[String] = None, ttl:Option[FiniteDuration] = None, data:Option[String] = None)

  case class KeyValue(createIndex:Int, modifyIndex:Int, lockIndex:Int, key:String, value:Option[String], session:Option[String])

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
  
  private[consul] case class SemaphoreData(permits:Int, holders:Set[SessionID]) {
    def decreasePermits():SemaphoreData = copy(permits = permits - 1)
    def increasePermits():SemaphoreData = copy(permits = permits + 1)
    def addHolder(sessionID: SessionID):SemaphoreData = copy(holders = holders + sessionID)
    def removeHolder(sessionID: SessionID): SemaphoreData = copy(holders = holders.filterNot(_ == sessionID))
    def hasPermits():Boolean = permits > 0
    def isHolder(sessionID: SessionID):Boolean = holders.contains(sessionID)
  }
}
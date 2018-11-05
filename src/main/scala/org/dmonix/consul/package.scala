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
  
  case class SetKeyValue(key:String, value:Option[String] = None, compareAndSet:Option[Int] = None, acquire:Option[SessionID] = None, release:Option[SessionID] = None)
  case class DeleteKeyValue(key:String, compareAndSet:Option[Int] = None, recursive:Boolean = false)
  
  case class SemaphoreData(permits:Int, holders:Set[SessionID]) {
    def decreasePermits():SemaphoreData = copy(permits = permits - 1)
    def increasePermits():SemaphoreData = copy(permits = permits + 1)
    def addHolder(sessionID: SessionID):SemaphoreData = copy(holders = holders ++ Set(sessionID))
    def removeHolder(sessionID: SessionID): SemaphoreData = copy(holders = holders.filterNot(_ == sessionID))
    def hasPermits():Boolean = permits > 0
    def isHolder(sessionID: SessionID):Boolean = holders.contains(sessionID)
  }
}
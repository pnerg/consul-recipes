package org.dmonix

/**
  * @author Peter Nerg
  */
package object consul {

  type SessionID = String

  
  case class ConsulHost(host:String, port:Int = 8500)
  
  case class Session(name:String, lockDelay:Option[Int] = None, node:Option[String] = None, behavior:Option[String] = None, ttl:Option[Int] = None)

  case class KeyValue(createIndex:Int, modifyIndex:Int, lockIndex:Int, key:String, value:Option[String], session:Option[String])
  
  case class SetKeyValue(key:String, value:Option[String], compareAndSet:Option[Int] = None, acquire:Option[SessionID] = None, release:Option[SessionID] = None)
}

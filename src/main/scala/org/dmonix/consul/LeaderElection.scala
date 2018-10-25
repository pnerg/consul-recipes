package org.dmonix.consul

import spray.json.JsValue

import scala.util.Try

/**
  * @author Peter Nerg
  */
object LeaderElection {
  def joinLeaderElection(consulHost: ConsulHost, groupName:String, info: Option[JsValue] = None): Try[Electee] = {
    val consul = Consul(consulHost)
    consul.createSession(Session(groupName)).map { sessionID =>
      new ElecteeImpl(consul, new ConsulHttpSender(consulHost), groupName, sessionID, info)
    }
  }
}

trait Electee {

  def isLeader:Boolean
  def leave():Unit
}

private class ElecteeImpl(consul:Consul, httpSender:HttpSender, groupName:String, sessionID:SessionID, info: Option[JsValue]) extends Electee {

  private val setKey = SetKeyValue(
    key = s"leader-election/$groupName",
    value = info.map(_.prettyPrint),
    acquire = Option(sessionID)
  )
  private var isActive:Boolean = true
  private var isLeaderState = attemptToTakeLeadership()
  private var changeIndex = 0

  override def isLeader: Boolean = isLeaderState

  override def leave(): Unit = {
    consul.destroySession(sessionID)
    isLeaderState = false
    isActive = false
  }

  private def attemptToTakeLeadership():Boolean = 
    consul.storeKeyValue(setKey) getOrElse false

}
package org.dmonix.consul

import spray.json.JsValue

import scala.util.{Success, Try}

/**
  * Factory for creating candidates for leader election
  * @author Peter Nerg
  */
object LeaderElection {
  /**
    * Create a candidate for leader election
    * @param consulHost Consul host
    * @param groupName The election group to join
    * @param info Optional information to be stored on the election key if/when this candidate becomes elected 
    * @param observer Optional observer to receive election updates
    * @return
    */
  def joinLeaderElection(consulHost: ConsulHost, groupName:String, info: Option[JsValue] = None, observer:Option[ElectionObserver] = None): Try[Candidate] = {
    val consul = Consul(consulHost)
    consul.createSession(Session(groupName)).map { sessionID =>
      new CandidateImpl(consul, new ConsulHttpSender(consulHost), groupName, sessionID, info, observer)
    }
  }
}

/**
  * Observer to be notified for changes in election state.
  */
trait ElectionObserver {
  /**
    * This candidate has been elected as leader.
    */
  def elected():Unit

  /**
    * This candidate has lost leadership.
    */
  def unElected():Unit
}

/**
  * Represents a candidate in the leader election
  */
trait Candidate {
  /**
    * If this candidate has been elected as leader.
    * @return
    */
  def isLeader:Boolean

  /**
    * Leaves the election process.
    * Should this candidate currently be leader the leadership is released.
    * Once invoked this candidate will no longer be part of the election process, refer to ''LeaderElection.joinLeaderElection'' 
    * to create a new candidate and re-join the election
    */
  def leave():Unit
}


import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import Implicits._

private class CandidateImpl(consul:Consul, httpSender:HttpSender, groupName:String, sessionID:SessionID, info: Option[JsValue], observer:Option[ElectionObserver]) extends Candidate {
  
  private val setKey = SetKeyValue(
    key = s"leader-election/$groupName",
    value = info.map(_.prettyPrint),
    acquire = Option(sessionID)
  )
  
  private val waitDuration = 60.seconds
  private var isActive:Boolean = true
  private var isLeaderState = attemptToTakeLeadership() //immediately try to cease leadership
  private var modifyIndex = 0

  override def isLeader: Boolean = isLeaderState
  
  waitForElectionUpdate()

  override def leave(): Unit = {
    isActive = false
    val deleteKey = setKey.copy(acquire = None, release = Option(sessionID))
    consul.storeKeyValue(deleteKey) //release the ownership, we do this even if we don't own the key doesn't matter
    consul.destroySession(sessionID) //delete our session
    if(isLeaderState) 
      notifyUnElected()
    isLeaderState = false
  }

  private def attemptToTakeLeadership():Boolean = {
    consul.storeKeyValue(setKey) match {
      case Success(true) if !isLeader => //acquired leadership
        notifyElected()
        true
      case Success(false) if isLeader =>  //lost leadership
        notifyUnElected()
        false
      case Success(newLeaderState) => //unchanged state 
        newLeaderState
      case _ => //failed to access Consul
        false
    }
  }
  
  private def waitForElectionUpdate():Unit = {
    Future(consul.readKeyValueWhenChanged(setKey.key, modifyIndex, waitDuration))
      .flatten
      .filter(_ => isActive) //fail the Future in case we're no longer active
      .onComplete {
        //successful response from Consul with a key
        case Success(Some(keyValue)) =>
          modifyIndex = keyValue.modifyIndex
          
          keyValue.session match {
            //election node has no owner, fight for ownership
            //current owner yielded or the owning session was terminated  
            case None => 
              isLeaderState = attemptToTakeLeadership()

            //we have become owner, notify of the change...this should really not be possible
            case Some(ownerSession) if (ownerSession == sessionID) && !isLeader =>
              notifyElected()
              
            //we have lost ownership, notify of the change...a manual change in Consul can cause this
            case Some(ownerSession) if (ownerSession != sessionID) && isLeader =>
              notifyUnElected()
              
            //no change to owner state, just ignore  
            case _ =>
          }
          waitForElectionUpdate()
          
        //future/try failed...do a new get on the key again
        case _ if isActive =>
          waitForElectionUpdate()
        //future failed du to the 'filter' where we decided we're no longer active, just ignore and exit  
        case _ if !isActive =>
      }
  }
  
  private def notifyElected():Unit = observer.foreach(_.elected())
  private def notifyUnElected():Unit = observer.foreach(_.unElected())
}
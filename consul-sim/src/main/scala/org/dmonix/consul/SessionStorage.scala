package org.dmonix.consul

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.{Map, mutable}

object SessionStorage {
  def apply():SessionStorage = new SessionStorage()
}

/**
  * Internal class for managing storage of sessions
  * @author Peter Nerg
  */
class SessionStorage {

  private val sessionCounter = new AtomicInteger(0)
  private val sessions = mutable.Map[SessionID, Session]()

  /**
    * Adds a session
    * @param sessionID The id of the session
    * @param session The session to add
    * @return
    */
  def addSession(sessionID: SessionID, session:Session):Unit = sessions.put(sessionID, session)

  /**
    * Creates a session
    * @return The id of the created session
    */
  def createSession():SessionID = {
    val id = "session-"+sessionCounter.incrementAndGet()
    addSession(id, Session())
    id
  }

  /**
    * Get all stored sessions
    * @return
    */
  def getSessions:Map[SessionID, Session] = this.sessions.toMap

  /**
    * Get a specific session
    * @param sessionID The id of the session
    * @return
    */
  def getSession(sessionID:SessionID):Option[Session] = sessions.get(sessionID)

  /**
    * Removes the provided session
    * @param sessionID The id of the session
    * @return The removed session if such existed
    */
  def removeSession(sessionID: SessionID):Option[Session] = sessions.remove(sessionID)

  /**
    * Checks if the provided session exists
    * @param sessionID
    * @return
    */
  def sessionExists(sessionID:String):Boolean = sessions.contains(sessionID)

  /**
    * Count the stored sessions
    * @return
    */
  def sessionCount:Int = sessions.size
}

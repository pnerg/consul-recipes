/**
  *  Copyright 2020 Peter Nerg
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

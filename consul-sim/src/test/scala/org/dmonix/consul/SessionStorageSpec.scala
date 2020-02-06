package org.dmonix.consul

import org.specs2.mutable.Specification

/**
  * @author Peter Nerg
  */
class SessionStorageSpec extends Specification {

  "The session storage shall" >> {
    "be empty when created" >> {
      SessionStorage().sessionCount === 0
    }
    "allow for adding a session" >> {
      val storage = SessionStorage()
      val sessionID = "123"
      storage.addSession(sessionID, Session())
      storage.sessionCount === 1
      storage.sessionExists(sessionID) === true
      storage.getSession(sessionID) must beSome()
    }
    "allow for creating a session" >> {
      val storage = SessionStorage()
      val sessionID = storage.createSession()
      storage.sessionCount === 1
      storage.sessionExists(sessionID) === true
      storage.getSession(sessionID) must beSome()
      storage.getSessions.get(sessionID) must beSome()
    }
    "allow for creating multiple sessions" >> {
      val storage = SessionStorage()
      val sessionID = storage.createSession()
      storage.sessionCount === 1
      storage.sessionExists(sessionID) === true
      storage.getSession(sessionID) must beSome()

      val sessionID2 = storage.createSession()
      storage.sessionCount === 2
      storage.sessionExists(sessionID2) === true
      storage.getSession(sessionID2) must beSome()
    }
    "return None when fetching non existing session" >> {
      val storage = SessionStorage()
      val sessionID = "no-such"
      storage.sessionExists(sessionID) === false
      storage.getSession(sessionID) must beNone
    }
    "allow for removing non-existing session" >> {
      SessionStorage().removeSession("no-such") must beNone
    }
    "allow for removing existing session" >> {
      val storage = SessionStorage()
      val sessionID = storage.createSession()
      storage.removeSession(sessionID) must beSome()
    }
  }
}

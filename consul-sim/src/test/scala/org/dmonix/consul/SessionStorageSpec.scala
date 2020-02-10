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

/**
  * Tests for the [[SessionStorage]] class
  * @author Peter Nerg
  */
class SessionStorageSpec extends ConsulSpecification {

  "The session storage shall" >> {
    "be empty when created" >> {
      SessionStorage().sessionCount === 0
    }
    "allow for adding a session" >> {
      val storage = SessionStorage()
      val sessionID = "123"
      storage.addSession(sessionID, Session())
      storage.sessionCount === 1
      storage.assertSessionExists(sessionID)
    }
    "allow for creating a session" >> {
      val storage = SessionStorage()
      val sessionID = storage.createSession()
      storage.sessionCount === 1
      storage.assertSessionExists(sessionID)
      storage.getSessions.get(sessionID) must beSome()
    }
    "allow for creating multiple sessions" >> {
      val storage = SessionStorage()
      val sessionID = storage.createSession()
      storage.sessionCount === 1
      storage.assertSessionExists(sessionID)

      val sessionID2 = storage.createSession()
      storage.sessionCount === 2
      storage.assertSessionExists(sessionID2)
    }
    "return None when fetching non existing session" >> {
      val storage = SessionStorage()
      storage.assertSessionNotExists("no-such")
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

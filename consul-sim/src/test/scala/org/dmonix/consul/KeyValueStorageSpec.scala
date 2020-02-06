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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

/**
  * Tests for the [[KeyValueStorage]] class
  *
  * @author Peter Nerg
  */
class KeyValueStorageSpec extends ConsulSpecification {
  
  "The key storage shall" >> {
    "allow for adding a new key" >> {
      val key = "my-key"
      val value = Some("my-value")
      val storage = KeyValueStorage()
      storage.createOrUpdate(key, value, None, None, None) === true
      storage.assertKeyValue(key, value)
      storage.assertKeyExists(key)
    }
    "allow for updating a key" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()
      
      val newValue = Some("new-value")
      storage.createOrUpdate(kv.key, newValue, None, None, None) === true
      storage.assertKeyValue(kv.key, newValue)
    }
    "allow for updating a key with CAS provided the ModificationIndex has not changed" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      val newValue = Some("new-value")
      storage.createOrUpdate(kv.key, newValue, Some(kv.modifyIndex), None, None) === true
      storage.assertKeyValue(kv.key, newValue)
      storage.assertKeyExists(kv)
    }
    "not allow for updating a key with CAS if the ModificationIndex has changed" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      val newValue = Some("new-value")
      storage.createOrUpdate(kv.key, newValue, Some(kv.modifyIndex-1), None, None) === false //setting CAS to something else than the key has simulates a changed ModificationIndex
      storage.assertKeyValue(kv.key, kv.value) //the old value shall remain
      storage.assertKeyExists(kv)
    }
    "allow for deleting non-existing key" >> {
      KeyValueStorage().removeKey("no-such") must beNone
    }
    "allow for deleting existing key" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.removeKey(kv.key) must beSome().which(_.value == kv.value)
      storage.assertKeyNotExists(kv)
    }
    "immediately return a read where index criteria is met" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()
      
      storage.readKey(kv.key, 0, 0.seconds) must beSome().which(_.value == kv.value)
    }
    "block and return the non-changed key if duration is passed" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.readKey(kv.key, Int.MaxValue, 5.millis) must beSome().which(_.value == kv.value)
    }
    "block and return the the changed key " >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      val newValue = Some("new-value")
      val f = Future {
        val index = storage.getKeyValue(kv.key).get.modifyIndex
        storage.readKey(kv.key, index+1, 1.seconds)
      }
      //wait some time and then release the lock by updating the key
      Thread.sleep(100)
      storage.createOrUpdate(kv.key, newValue, None, None, None) === true
      
      //assert the future/lock has been released and we got the updated value
      Await.result(f, 2.seconds) must beSome().which(_.value == newValue)
    }
    "allow for acquiring a lock on a non-locked key" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.createOrUpdate(kv.key, kv.value, None, Some("my-session"), None) == true
    }
    "allow for re-acquiring a lock on for the same session" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.createOrUpdate(kv.key, kv.value, None, Some("my-session"), None) == true
      storage.createOrUpdate(kv.key, kv.value, None, Some("my-session"), None) == true
    }
    "fail to acquire a lock on for the if another owner" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.createOrUpdate(kv.key, kv.value, None, Some("my-session"), None) == true
      storage.createOrUpdate(kv.key, kv.value, None, Some("your-session"), None) == false
    }
    "allow to release a lock on if the session is the owner" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.createOrUpdate(kv.key, kv.value, None, Some("my-session"), None) == true
      storage.createOrUpdate(kv.key, kv.value, None, None, Some("my-session")) == true
    }
    "allow to release a lock on if the session is not the owner" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.createOrUpdate(kv.key, kv.value, None, Some("my-session"), None) == true
      storage.createOrUpdate(kv.key, kv.value, None, None, Some("your-session")) == false
    }
    "fail to release a lock on a non-locked key" >> {
      val storage = KeyValueStorage()
      val kv = storage.createInitialKey()

      storage.createOrUpdate(kv.key, kv.value, None, None, Some("my-session")) == false
    }
  }
  
  
}

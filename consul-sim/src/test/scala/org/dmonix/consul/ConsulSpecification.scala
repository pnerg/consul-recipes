package org.dmonix.consul

import java.util.concurrent.atomic.AtomicInteger

import org.specs2.mutable.Specification

object ConsulSpecification {
  private val keyCreationCounter = new AtomicInteger(1)


}
import ConsulSpecification._
/**
  * @author Peter Nerg
  */
trait ConsulSpecification extends Specification {
  
  implicit class PimpedKeyValueStorage(storage:KeyValueStorage) {
    def createInitialKey():KeyValue = {
      val key = "my-key-"+keyCreationCounter.getAndIncrement()
      val value = Option("some-value")

      storage.createOrUpdate(key,value, None, None, None) === true
      storage.keyExists(key) === true
      storage.getKeyValue(key).get
    }

    def assertKeyValue(key:String, expectedValue:Option[String]) = storage.getKeyValue(key) must beSome().which(_.value == expectedValue)
    def assertKeyExists(kv:KeyValue) = storage.keyExists(kv.key) === true
    def assertKeyExists(key:String) = storage.keyExists(key) === true
    def assertKeyNotExists(kv:KeyValue) = storage.keyExists(kv.key) === false
    def assertKeyNotExists(key:String) = storage.keyExists(key) === false
  }

  implicit class PimpedSessionStorage(storage:SessionStorage) {
    def assertSessionExists(sessionID: SessionID) = {
      storage.sessionExists(sessionID) === true
      storage.getSession(sessionID) must beSome()
    }
    def assertSessionNotExists(sessionID: SessionID) = {
      storage.sessionExists(sessionID) === false
      storage.getSession(sessionID) must beNone
    }
  }
}

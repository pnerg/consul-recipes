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

import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{TimeUnit, Semaphore => jSemaphore}

import org.slf4j.LoggerFactory

import scala.collection.{Seq, mutable}
import scala.concurrent.duration.FiniteDuration

object KeyValueStorage {
  private case class Blocker(index:Int, semaphore: jSemaphore) {
    def releaseIfIndexReached(mi:Int):Unit = if(mi >= index)semaphore.release()
  }

  def apply(): KeyValueStorage = new KeyValueStorage()
  
}

import org.dmonix.consul.KeyValueStorage._

/**
  * Internal class for managing storage of key/values
  * @author Peter Nerg
  */
class KeyValueStorage {
  private val logger = LoggerFactory.getLogger(classOf[KeyValueStorage])

  private val creationCounter = new AtomicInteger(0)
  private val modificationCounter = new AtomicInteger(0)

  private val keyValues = mutable.Map[Path, KeyValue]()
  private val blockers = mutable.Map[Path, Seq[Blocker]]()

  implicit class PimpedString(s:String) {
    def asPath:Path = Paths.get(s)
  }
  
  /**
    * Get all stored key/values
    * @return
    */
  def getKeyValues:Map[String, KeyValue] =  keyValues.map(kv => (kv._1.toString, kv._2)).toMap

  /**
    * In essence a non-blocking 'readKey'
    * @param key
    * @return
    */
  def getKeyValue(key: String):Option[KeyValue] =  keyValues.get(key.asPath)

  /**
    * Check if the provided key exists
    * @param key
    * @return
    */
  def keyExists(key:String):Boolean =  keyValues.contains(key.asPath)


  /**
    * Attempts to create/update the provided key
    * @param key The name/path of the key
    * @param value The new (optional) value
    * @return
    * @since 0.5.0
    */
  def createOrUpdate(key:String, value:Option[String]): Boolean = createOrUpdate(key, value, None, None, None, None)
  
  /**
    * Attempts to create/update the provided key
    * @param key The name/path of the key
    * @param newValue The new (optional) value
    * @param cas Optional compare-and-set
    * @param acquire Optional acquire lock
    * @param release Optional release
    * @return
    */
  def createOrUpdate(key:String, newValue:Option[String], cas:Option[Int], acquire:Option[String], release:Option[String], flags:Option[Int]): Boolean = {
    val kv = getKeyValue(key)
      .map(_.copy(value = newValue))
      .getOrElse(KeyValue(createIndex = nextCreationIndex, modifyIndex = 0, lockIndex = 0, flags = flags.getOrElse(0), key = key, session = None, value = newValue))
    attemptSetKey(kv, cas, acquire, release)    
  }
  
  /**
    * Simulates a recursive fetch of keys matching a path e.g /foo/bar
    * @param path
    * @return
    */
  def getKeysForPath(path:String):Seq[KeyValue] =  keyValues.filterKeys(_.startsWith(path)).values.toSeq

  /**
    * Removes the key and releases any potential blockers
    * @param key
    * @return
    */
  def removeKey(key:String):Option[KeyValue] = synchronized {
    val keyValue = keyValues.remove(key.asPath)
    keyValue.foreach{kv =>
      blockers.get(kv.key.asPath).foreach(_.foreach(_.semaphore.release()))
    }
    keyValue
  }

  /**
    * Attempts to perform a blocking read of a key
    * @param key The key to read
    * @param index The modification index to block on
    * @param wait The duration to block for
    * @return
    */
  def readKey(key:String, index:Int, wait:FiniteDuration):Option[KeyValue] = {
    getKeyValue(key).flatMap{kv =>
      if(index <= kv.modifyIndex)
        Some(kv)
      else {
        val kvPath = kv.key.asPath
        val semaphore = new java.util.concurrent.Semaphore(0)
        val blocker = Seq(Blocker(index, semaphore))
        val seq = blockers.get(kvPath) map(_ ++ blocker) getOrElse blocker
        blockers.put(kvPath, seq)
        logger.debug(s"Found key [$key] but index is [${kv.modifyIndex}], adding blocker on index [$index] with wait [${wait.toSeconds}]s")
        //hold here until the time runs out of someone updates the key
        semaphore.tryAcquire(wait.toMillis, TimeUnit.MILLISECONDS)
        //either the semaphore was released or the duration has passed. Try to read the key again now without blocking
        getKeyValue(key)
      }
    }
  }

  private def attemptSetKey(kv: KeyValue, cas:Option[Int], acquire:Option[String], release:Option[String]): Boolean = synchronized {
    val passedCAS = cas.map(_ == kv.modifyIndex) getOrElse true //default to true if no CAS is provided
    def isUnlocked:Boolean = kv.session.isEmpty
    def isLockOwner(sessionID:String): Boolean = kv.session.map(_ == sessionID) getOrElse false

    val res = (acquire, release) match {
      //compare-and-set failed, bail out
      case _ if !passedCAS => None
      //trying to take lock with no owner
      case (Some(_), None) if isUnlocked => Some(kv.copy(modifyIndex = nextModificationIndex, session = acquire, lockIndex = kv.lockIndex+1))
      //trying to take lock whilst owning it       
      case (Some(id), None) if isLockOwner(id) => Some(kv.copy(modifyIndex = nextModificationIndex, session = acquire))
      //trying to take lock whilst NOT owning it       
      case (Some(id), None) if !isLockOwner(id) => None
      //trying to release lock with no owner   
      case (None, Some(_)) if isUnlocked => None
      //trying to release lock whilst owning it       
      case (None, Some(id)) if isLockOwner(id) => Some(kv.copy(modifyIndex = nextModificationIndex, session = None))
      //trying to release lock whilst NOT owning it       
      case (None, Some(id)) if !isLockOwner(id) => None
      //neither 'acquire' nor 'release' has been provided, just write the data
      case _ => Some(kv.copy(modifyIndex = nextModificationIndex, session = None))
    }

    res.foreach{ kv =>
      val keyPath = kv.key.asPath
      keyValues.put(keyPath, kv)
      logger.debug(s"Storing key/value [$kv]")
      //notify any lock holders that the key has changed
      //we don't care to prune used Semaphores, sure this will leak objects but for a test rig it won't matter.
      //only release those that have a 'index' <= than the ModifyIndex on the key
      blockers.get(keyPath).foreach(_.foreach(_.releaseIfIndexReached(kv.modifyIndex)))
    }
    res.isDefined
  }

  private def nextCreationIndex:Int =  creationCounter.getAndIncrement()
  private def nextModificationIndex:Int =  modificationCounter.getAndIncrement()

}

/**
  *  Copyright 2018 Peter Nerg
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


import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}
import scala.util._
import spray.json._
import ConsulJsonProtocol._
import org.dmonix.consul.Semaphore.sessionTTL
import org.slf4j.LoggerFactory
import Implicits._

/**
  * Companion object to [[Semaphore]]
  */
object Semaphore {
  private[consul] val sessionTTL = 10.seconds
  private[consul] val permitFileName = ".permits"
  private[consul] case class AggregatedData(semaphoreKeyFile: KeyValue, semaphoreData: SemaphoreData, holderFiles:Stream[KeyValue]) {
    def staleMemberFiles:Stream[KeyValue] = holderFiles.filter(_.session.isEmpty) //all member/session files without an owner
    def validMemberFiles:Stream[KeyValue] = holderFiles.filter(_.session.isDefined) //all member/session files with an owner
    def validHolderIDs:Set[SessionID] = validMemberFiles.map(_.session).flatten.toSet
    def isHolder(sessionID: SessionID):Boolean = validHolderIDs.contains(sessionID)
  }
  
  /**
    * Creates a Semaphore with the provided number of permits
    * @param consulHost Consul host
    * @param semaphoreName The name to use for the Semaphore in Consul
    * @param initialPermits The initial number of permits available. This value may be negative, in which case releases must occur before any acquires will be granted
    * @return
    */
  def apply(consulHost: ConsulHost, semaphoreName:String, initialPermits:Int):Try[Semaphore] = {
    Try(require(initialPermits > 0, s"'initialPermits' must be a positive value but [$initialPermits] was provided"))
      .flatMap{ _ =>
        val consul = createConsul(consulHost)
        val semaphorePath = "semaphores/"+semaphoreName
        val lockData = SemaphoreData(initialPermits, Set.empty).toJson.prettyPrint

        consul
          .storeKeyValueIfNotSet(lockFile(semaphorePath), Some(lockData))
          .map(_ => new Semaphore(consul, semaphoreName))
      }
  }
  
  private def lockFile(path:String) = path + "/" + permitFileName
  private[consul] implicit class RichKeyValue(kv: KeyValue) {
    def isPermitFile:Boolean = kv.key.endsWith(permitFileName)
  }

  /**
    * Destroys all data stored in Consul for a Semaphore.  
    * Purpose is to clean out stale data.
    * @param consulHost Consul host
    * @param semaphoreName The name to use for the Semaphore in Consul
    * @return
    */
  def destroy(consulHost: ConsulHost, semaphoreName:String):Try[Boolean] = createConsul(consulHost).deleteKeyValueRecursive("semaphores/"+semaphoreName)

  private def createConsul(consulHost: ConsulHost) = new Consul(new ConsulHttpSender(consulHost)) with SessionUpdater
}


import Semaphore._

/**
  * A semaphore is a construction to control access to a common resource in a concurrent system.  
  * The semaphore in this library allows for creation of a distributed lock.   
  * In principle a semaphore is initiated with ''1..n'' permits, instances of a semaphore then try to acquire one permit either succeeding or failing in case there are no more permits left to take.  
  * The simplest form is a binary semaphore which has only one permit thus only allowing a single instance to acquire a permit.  
  * Semaphores are traditionally used to control access to a protected source like a database or some task that only should be executed by a single process.     
  *
  * Each instance of the Semaphore class can hold exactly 0 or 1 permit thus invoking any of the ''tryAcquire'' functions multiple times will not acquire additional permits.
  *
  * @author Peter Nerg
  */
class Semaphore(consul:Consul with SessionUpdater, semaphoreName:String) {
  private val logger = LoggerFactory.getLogger(classOf[Semaphore])

  private val semaphorePath = "semaphores/"+semaphoreName
  private val permitFile = Semaphore.lockFile(semaphorePath)
  
  @volatile private var sessionIDOpt:Option[SessionID] = None

  /**
    * The Consul session ID for this Semaphore instance
    * @return
    * @since 1.0.0
    */
  def sessionID:Option[String] = sessionIDOpt
  
  /**
    * Attempts to release a permit.
    * If this instance is not holding a permit this function call does nothing
    * @return @return ''Success'' if managed to access Consul, then ''true'' if a permit was released.
    */
  def release():Try[Boolean] = {
    sessionIDOpt match {
      case Some(sessionID) =>
        (for {
          _ <- consul.deleteKeyValue(memberFile(sessionID))
          _ <- consul.destroySession(sessionID)
          _ <- Try(consul.unregisterSession(sessionID))
          aggregatedData <- readSemaphoreInfo()
        } yield {
          //only allowed to release if owner/holder of a permit
          if(aggregatedData.semaphoreData.isHolder(sessionID)) {
            //attempt to write back lock data with the added permits
            val newData = aggregatedData.semaphoreData.removeHolder(sessionID)
            storeLockData(newData, aggregatedData.semaphoreKeyFile.modifyIndex)
              .flatMap {
                //lock successfully updated
                case true =>
                  logger.debug("[{}] successfully released permit for [{}]", sessionID, semaphoreName:Any)
                  Success(true)
                //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
                case false =>
                  logger.debug("[{}] failed to release permit for [{}] due to concurrent write, will try again", sessionID, semaphoreName:Any)
                  release()
              }
          }
          //not an owner/holder of a permit
          else
            Success(false)
        }).flatten
      //don't even have a session, cannot be permit holder
      case None => Success(false)
    }
  }

  /**
    * Destroys all data for the semaphore. 
    * Any instances blocking for a permit will be released and return as ''Failure'',
    * @return
    */
  def destroy():Try[Boolean] = release().flatMap(_ => consul.deleteKeyValueRecursive("semaphores/"+semaphoreName)) 
  
  /**
    * Attempts to acquire a permit without blocking.  
    * If this instance is already holding a permit no further permits are taken
    * @return ''Success'' if managed to access Consul, then ''true'' if a permit was acquired.
    */
  def tryAcquire():Try[Boolean] = tryAcquireInternal().map(_._1)

  /**
    * Attempts to acquire a permit blocking if necessary.
    * If this instance is already holding a permit no further permits are taken
    * In practice this invokes 
    * {{{
    * tryAcquire(Deadline.now + maxWait) 
    * }}}
    * @param maxWait The maximum time to block for a permit
    * @return ''Success'' if managed to access Consul, then ''true'' if a permit was acquired.
    */
  def tryAcquire(maxWait:FiniteDuration):Try[Boolean] = tryAcquire(Deadline.now + maxWait)

  /**
    * Attempts to acquire a permit blocking if necessary.
    * If this instance is already holding a permit no further permits are taken
    * @param deadline The deadline for when to give up blocking for a permit
    * @return ''Success'' if managed to access Consul, then true if a permit was acquired.
    */
  def tryAcquire(deadline: Deadline):Try[Boolean] = {
    //there's still time left to do attempts
    if(deadline.hasTimeLeft()) {
      tryAcquireInternal().flatMap {
        //got the lock, return
        case (true, _) => 
          Success(true)
        //did not get the lock, block on the lock file and try again
        //the readLockData returns if the lockData file has changed or the waitTime expires  
        case (false, aggregatedData) if deadline.hasTimeLeft() => 
          logger.debug("No permits left for [{}], will block on index [{}] for max [{}] waiting for an update", semaphoreName,aggregatedData.semaphoreKeyFile.modifyIndex+1, deadline.timeLeft:Any)
          readSemaphoreInfo(aggregatedData.semaphoreKeyFile.modifyIndex+1, deadline.timeLeft).flatMap{ _ => tryAcquire(deadline)}
        //didn't get lock and we're passed the deadline, bail out
        case (false, _) =>
          logger.debug("Failed to acquire permit for [{}] within the required time frame", semaphoreName)
          Success(false)
      }
    }
    //we've reached the max wait time, bail out  
    else {
      logger.debug("Failed to acquire permit for [{}] within the required time frame", semaphoreName)
      Success(false)
    }
  }

  private def tryAcquireInternal():Try[(Boolean, AggregatedData)] = {
    (for {
      sessionID <- getOrCreateSession()
      rawData <- readSemaphoreInfo()  //try to read the lock data
      aggregatedData <- pruneStaleHolders(rawData) //prune any potential holders and return a mutated AggregatedData
    } yield {
      logger.debug("[{}] read data for [{}] current state is [{}]", sessionID, semaphoreName, aggregatedData.semaphoreData:Any)
      //if already a holder, return true
      if(aggregatedData.isHolder(sessionID)) {
        logger.debug("[{}] is already a holder of a permit for [{}]", sessionID, semaphoreName:Any)
        Success((true, aggregatedData))
      }
      //if there's enough permits left, try to take one
      else if(aggregatedData.semaphoreData.hasPermits()) {
        //create new data ourselves as holder
        val newData = aggregatedData.semaphoreData.addHolder(sessionID)
        //attempt to write the updated lock data
        logger.debug("[{}] attempts to acquire permit for [{}] with updated data [{}]", sessionID, semaphoreName, newData:Any)
        storeLockData(newData, aggregatedData.semaphoreKeyFile.modifyIndex)
          .flatMap{
            //data written, we got the lock/semaphore
            case true =>
              logger.debug(s"[{}] successfully acquired permit for [{}]", sessionID, semaphoreName)
              writeOwnMemberFile(sessionID) //as we acquired the lock we must write our own member/lockholder file
                .map(_ => (true, aggregatedData)) //map/return the result of the permit acquire
            //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
            case false =>
              logger.debug("[{}] failed to write lock data for [{}] due to concurrent changes, will try again", sessionID, semaphoreName:Any)
              tryAcquireInternal()
          }
      }
      //not enough permits left, bail out  
      else {
        logger.debug("[{}] could not acquire permit to [{}] due to lack of permits", sessionID, semaphoreName)
        Success((false, aggregatedData))
      } 
    }).flatten
      
  }

  private def writeOwnMemberFile(sessionID: SessionID):Try[Unit] = {
    consul
      .storeKeyValue(SetKeyValue(key = memberFile(sessionID), acquire = Some(sessionID))) //attempt to write own 'member' file
      .flatMap {
        case true => Success(())
        //should really not happen as we use our own sessionID as key
        case false => Failure(new IllegalStateException(s"Failed to create path [${semaphorePath+"/"+sessionID}] cannot join in Semaphore group"))
    }
  }
  
  private def storeLockData(semaphoreData: SemaphoreData, modifyIndex:Int):Try[Boolean] =
    consul
      .storeKeyValue(SetKeyValue(permitFile, semaphoreData.toJson).withCompareAndSet(modifyIndex))

  /**
    * Attempts to the read the ''.lock'' file for the Semaphore 
    * @param modifyIndex Optional ModifyIndex, used if blocking for change on the file
    * @param maxWait Optional waitTime, used with the modifyIndex when blocking for a change
    * @return
    */
  private def readSemaphoreInfo(modifyIndex:Int = 0, maxWait:FiniteDuration = Consul.zeroDuration):Try[AggregatedData] = {
    (for{
      semaphorePermitFile <- consul.readKeyValueWhenChanged(permitFile, modifyIndex, maxWait)
      memberFiles <- readMemberFiles
    } yield {
      semaphorePermitFile match {
        case Some(kv) => kv.convertValueTo[SemaphoreData] match {
          case Some(data) => Success(AggregatedData(kv, data, memberFiles))
          case None => Failure(new IllegalStateException(s"The data for path [$permitFile] has been erased"))
        }
        //the data for the semaphore has been deleted, could be due to invoking "destroy", just bail out
        case None => Failure(SemaphoreDestroyed(semaphoreName))
      }
    }).flatten
  }

  private def readMemberFiles:Try[Stream[KeyValue]] = 
    consul.readKeyValueRecursive(semaphorePath)
      .map(_.getOrElse(Stream.empty))
       .map(_.filterNot(_.isPermitFile))
  
  private def getOrCreateSession():Try[SessionID] =
    sessionIDOpt.asTry()
        .recoverWith { case _ =>
          consul.createSession(Session(name = Option(semaphoreName), ttl = Option(sessionTTL))) match {
            case Success(sessionID) =>
              sessionIDOpt = Some(sessionID)
              consul.registerSession(sessionID, sessionTTL)
              logger.debug("Created session [{}] for [{}]", sessionID, semaphoreName)
              Success(sessionID)
            case f@Failure(_) => f
          }
        }
  
  private def memberFile(sessionID: SessionID):String = semaphorePath + "/" +sessionID

  /**
    * Finds if there are any stale sessions registered as holders on the ''.semaphore'' file.
    * This is done by comparing the sessions in the file with the session files in the same directory.  
    * Should there be a mismatch it means that an application has crashed and Consul has reaped the session file
    * @return
    */
  private def pruneStaleHolders(aggregatedData: AggregatedData):Try[AggregatedData] = {
    //any member file without owner is a sign of a session that has died and orphaned the file, nuke it
    aggregatedData.staleMemberFiles.foreach {kv =>
      logger.warn("Found member file [{}] without owner for [{}], this is the mark of a dead session will delete it", kv.key, semaphoreName)
      consul.deleteKeyValue(kv.key)
    }
    //set the valid holders
    val newSemData = aggregatedData.semaphoreData.copy(holders = aggregatedData.validHolderIDs)
    Success(aggregatedData.copy(semaphoreData = newSemData))
  }
}

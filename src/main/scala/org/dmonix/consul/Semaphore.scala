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
    def valueAsPermitData:Option[SemaphoreData] = kv.value.map(_.parseJson.convertTo[SemaphoreData])
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
  * Each instance of the Semaphore class can hold exactly 0 or 1 permit thus invoking any of the ''tryAcquire'' functions mulitple times will not acquire additional permits.
  *
  * @author Peter Nerg
  */
class Semaphore(consul:Consul with SessionUpdater, semaphoreName:String) {
  private val logger = LoggerFactory.getLogger(classOf[Semaphore])

  private val semaphorePath = "semaphores/"+semaphoreName
  private val permitFile = Semaphore.lockFile(semaphorePath)
  
  private var sessionIDOpt:Option[SessionID] = None

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
                  logger.debug(s"[$sessionID] successfully released permit for [$semaphoreName]")
                  Success(true)
                //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
                case false =>
                  logger.debug(s"[$sessionID] failed to release permit for [$semaphoreName] due to concurrent write, will try again")
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
  def destroy():Try[Boolean] = release.flatMap(_ => consul.deleteKeyValueRecursive("semaphores/"+semaphoreName)) 
  
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
        case (false, aggregatedData) if deadline.hasTimeLeft => 
          logger.debug(s"No permits left for [$semaphoreName], will block on index [${aggregatedData.semaphoreKeyFile.modifyIndex+1}] for max [${deadline.timeLeft}] waiting for an update")
          readSemaphoreInfo(aggregatedData.semaphoreKeyFile.modifyIndex+1, deadline.timeLeft).flatMap{ _ => tryAcquire(deadline)}
        //didn't get lock and we're passed the deadline, bail out
        case (false, _) =>
          logger.debug(s"Failed to acquire permit for [$semaphoreName] within the required time frame")
          Success(false)
      }
    }
    //we've reached the max wait time, bail out  
    else {
      logger.debug(s"Failed to acquire permit for [$semaphoreName] within the required time frame")
      Success(false)
    }
  }

  private def tryAcquireInternal():Try[(Boolean, AggregatedData)] = {
    (for {
      sessionID <- getOrCreateSession()
      rawData <- readSemaphoreInfo()  //try to read the lock data
      aggregatedData <- pruneStaleHolders(rawData) //prune any potential holders and return a mutated AggregatedData
    } yield {
      //if already a holder, return true
      if(aggregatedData.isHolder(sessionID)) {
        logger.debug(s"[$sessionID] is already a holder of a permit for [$semaphoreName]")
        Success((true, aggregatedData))
      }
      //if there's enough permits left, try to take one
      else if(aggregatedData.semaphoreData.hasPermits) {
        //create new data ourselves as holder
        val newData = aggregatedData.semaphoreData.addHolder(sessionID)
        //attempt to write the updated lock data
        logger.debug(s"[$sessionID] attempts to acquire permit for [$semaphoreName] with updated data [$newData]")
        storeLockData(newData, aggregatedData.semaphoreKeyFile.modifyIndex)
          .flatMap{
            //data written, we got the lock/semaphore
            case true =>
              logger.debug(s"[$sessionID] successfully acquired permit for [$semaphoreName]")
              writeOwnMemberFile(sessionID) //as we acquired the lock we must write our own member/lockholder file
                .map(_ => (true, aggregatedData)) //map/return the result of the permit acquire
            //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
            case false =>
              logger.debug(s"[$sessionID] failed to write lock data for [$semaphoreName] due to concurrent changes, will try again")
              tryAcquireInternal()
          }
      }
      //not enough permits left, bail out  
      else {
        logger.debug(s"[$sessionID] could not acquire permit to [$semaphoreName] due to lack of permits")
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
      .storeKeyValue(SetKeyValue(key = permitFile, compareAndSet = Some(modifyIndex), value = Option(semaphoreData.toJson.prettyPrint)))

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
        case Some(kv) => kv.valueAsPermitData match {
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
    sessionIDOpt.asTry
        .recoverWith { case _ =>
          consul.createSession(Session(name = Option(semaphoreName), ttl = Option(sessionTTL))) match {
            case Success(sessionID) =>
              sessionIDOpt = Some(sessionID)
              consul.registerSession(sessionID, sessionTTL)
              logger.debug(s"Created session [$sessionID] for [$semaphoreName]")
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
      logger.warn(s"Found member file [${kv.key}] without owner for [$semaphoreName], this is the mark of a dead session will delete it")
      consul.deleteKeyValue(kv.key)
    }
    //set the valid holders
    val newSemData = aggregatedData.semaphoreData.copy(holders = aggregatedData.validHolderIDs)
    Success(aggregatedData.copy(semaphoreData = newSemData))
  }
}

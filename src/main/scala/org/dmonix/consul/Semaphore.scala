package org.dmonix.consul


import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}
import scala.util._
import spray.json._
import ConsulJsonProtocol._
import org.dmonix.consul.Semaphore.sessionTTL
import org.slf4j.LoggerFactory
import Implicits._

object Semaphore {
  private[consul] val sessionTTL = 10.seconds

  /**
    * Creates a Semaphore with the provided number of permits
    * @param consulHost Consul host
    * @param semaphoreName The name to use for the Semaphore in Consul
    * @param initialPermits The initial number of permits available. This value may be negative, in which case releases must occur before any acquires will be granted
    * @return
    */
  def apply(consulHost: ConsulHost, semaphoreName:String, initialPermits:Int):Try[Semaphore] = {
    val sender = new ConsulHttpSender(consulHost)
    val consul = new Consul(sender) with SessionUpdater
    val semaphorePath = "semaphores/"+semaphoreName
    val lockData = SemaphoreData(initialPermits, Set.empty).toJson.prettyPrint

    consul
      .storeKeyValueIfNotSet(lockFile(semaphorePath), Some(lockData))
      .map(_ => new Semaphore(consul, semaphoreName))
  }
  
  private def lockFile(path:String) = path + "/.lock"
}

/**
  * @author Peter Nerg
  */
class Semaphore(consul:Consul with SessionUpdater, semaphoreName:String) {
  private val logger = LoggerFactory.getLogger(classOf[Semaphore])

  private val semaphorePath = "semaphores/"+semaphoreName
  private val lockFile = Semaphore.lockFile(semaphorePath)
  
  private var sessionIDOpt:Option[SessionID] = None
  private case class AggregatedData(keyValue: KeyValue, semaphoreData: SemaphoreData)

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
          aggregatedData <- readLockData()
          _ <- consul.destroySession(sessionID)
        } yield {
          consul.unregisterSession(sessionID)
          //only allowed to release if owner/holder of a permit
          println(aggregatedData)
          if(aggregatedData.semaphoreData.isHolder(sessionID)) {
            //attempt to write back lock data with the added permits
            val newData = aggregatedData.semaphoreData.increasePermits().removeHolder(sessionID)
            storeLockData(newData, aggregatedData.keyValue.modifyIndex)
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
      case None => Success(false)
    }
  }

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
        case (true, _) => Success(true)
        //did not get the lock, block on the lock file and try again
        //the readLockData returns if the lockData file has changed or the waitTime expires  
        case (false, aggregatedData) if deadline.hasTimeLeft => readLockData(aggregatedData.keyValue.modifyIndex+1, deadline.timeLeft).flatMap{ _ => tryAcquire(deadline)}
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
      _ <- writeOwnMemberFile(sessionID) //always write our own member/holder file
      aggregatedData <- readLockData()  //try to read the lock data
    } yield {
      //if already a holder, return true
      if(aggregatedData.semaphoreData.isHolder(sessionID)) {
        Success((true, aggregatedData))
      }
      //if there's enough permits left, try to take them
      else if(aggregatedData.semaphoreData.hasPermits) {
        //create new data with decreased permits and ourselves as holder
        val newData = aggregatedData.semaphoreData.decreasePermits().addHolder(sessionID)
        //attempt to write the updated lock data
        logger.debug(s"[$sessionID] attempts to acquire permit for [$semaphoreName] with [$newData]")
        storeLockData(newData, aggregatedData.keyValue.modifyIndex)
          .flatMap{
            //data written, we got the lock/semaphore
            case true =>
              logger.debug(s"[$sessionID] successfully acquired permit for [$semaphoreName]")
              Success((true, aggregatedData))
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
      .storeKeyValue(SetKeyValue(key = lockFile, compareAndSet = Some(modifyIndex), value = Some(semaphoreData.toJson.prettyPrint)))

  /**
    * Attempts to the read the ''.lock'' file for the Semaphore 
    * @param modifyIndex Optional ModifyIndex, used if blocking for change on the file
    * @param maxWait Optional waitTime, used with the modifyIndex when blocking for a change
    * @return 
    */
  private def readLockData(modifyIndex:Int = 0, maxWait:FiniteDuration = Consul.zeroDuration):Try[AggregatedData] = 
    consul
      .readKeyValueWhenChanged(lockFile, modifyIndex, maxWait)
      .flatMap{ 
        case Some(kv) => kv.value.map(_.parseJson.convertTo[SemaphoreData]) match {
          case Some(data) => Success(AggregatedData(kv, data))
          case None => Failure(new IllegalStateException(s"The data for path [$lockFile] has been erased"))
         }
        case None => Failure(new IllegalStateException(s"The path [$lockFile] no longer exists"))
      }
  
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
}

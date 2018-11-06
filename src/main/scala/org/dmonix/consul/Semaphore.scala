package org.dmonix.consul


import scala.concurrent.duration.{Deadline, DurationInt, FiniteDuration}
import scala.util._
import spray.json._
import ConsulJsonProtocol._

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
    
    def lockData(sessionID:SessionID):String = SemaphoreData(initialPermits, Set.empty).toJson.prettyPrint
    
    for {
      sessionID <- consul.createSession(Session(name = Option(semaphoreName), ttl = Option(sessionTTL))) //create the sessionID
      _ <- consul.storeKeyValueIfNotSet(lockFile(semaphorePath), Some(lockData(sessionID))) //attempt to create the .lock file if it doesn't exist
    } yield new Semaphore(consul, sessionID, semaphorePath)
    
  }
  
  private def lockFile(path:String) = path + "/.lock"
}

/**
  * @author Peter Nerg
  */
class Semaphore(consul:Consul with SessionUpdater, sessionID: SessionID, semaphorePath:String) {
  private val lockFile = Semaphore.lockFile(semaphorePath)
  private val memberFile = semaphorePath+"/"+sessionID
  
  private case class AggregatedData(keyValue: KeyValue, semaphoreData: SemaphoreData)

  /**
    * Attempts to release a permit.
    * If this instance is not holding a permit this function call does nothing
    * @return @return ''Success'' if managed to access Consul, then ''true'' if a permit was released.
    */
  def release():Try[Boolean] = {
    consul
      .deleteKeyValue(memberFile) //delete our own member file
      .flatMap(_ => readLockData()) //attempt to read lock data
      .flatMap{aggregatedData => 
        //only allowed to release if owner/holder of a permit
        if(aggregatedData.semaphoreData.isHolder(sessionID)) {
          //attempt to write back lock data with the added permits
          val newData = aggregatedData.semaphoreData.increasePermits().removeHolder(sessionID)
          storeLockData(newData, aggregatedData.keyValue.modifyIndex)
            .flatMap {
              //lock successfully updated
              case true => Success(true)
              //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
              case false => release()
            }
        }
        //not an owner/holder of a permit
        else
          Success(false)
      }.map{res =>
      //TODO where to remove the session? Can't be done here as it would be impossible to acquire a permit again
//        consul.unregisterSession(sessionID)
//        consul.destroySession(sessionID)
        res
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
        case (false, aggregatedData) => readLockData(aggregatedData.keyValue.modifyIndex, deadline.timeLeft).flatMap( _ => tryAcquire(deadline))
      }
    }
    //we've reached the max wait time, bail out  
    else {
      Success(false)
    }
  }

  private def tryAcquireInternal():Try[(Boolean, AggregatedData)] = {
    writeOwnMemberFile() //always write our own member/holder file
      .flatMap(_ => readLockData()) //try to read the lock data
      .flatMap{ aggregatedData =>
      //if already a holder, return true
      if(aggregatedData.semaphoreData.isHolder(sessionID)) Success((true, aggregatedData))
      //if there's enough permits left, try to take them
      else if(aggregatedData.semaphoreData.hasPermits) {
        //create new data with decreased permits and ourselves as holder
        val newData = aggregatedData.semaphoreData.decreasePermits().addHolder(sessionID)
        //attempt to write the updated lock data
        storeLockData(newData, aggregatedData.keyValue.modifyIndex)
          .flatMap{
            //data written, we got the lock/semaphore
            case true => Success((true, aggregatedData))
            //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
            case false => tryAcquireInternal()
          }
      }
      //not enough permits left, bail out  
      else Success((false, aggregatedData))
    }
  }

  private def writeOwnMemberFile():Try[Unit] = 
    consul
      .storeKeyValue(SetKeyValue(key = memberFile, acquire = Some(sessionID))) //attempt to write own 'member' file
      .flatMap {
        case true => Success(())
        //should really not happen as we use our own sessionID as key
        case false => Failure(new IllegalStateException(s"Failed to create path [${semaphorePath+"/"+sessionID}] cannot join in Semaphore group"))
      }
  
  private def storeLockData(semaphoreData: SemaphoreData, modifyIndex:Int):Try[Boolean] = 
    consul
      .storeKeyValue(SetKeyValue(key = lockFile, compareAndSet = Some(modifyIndex), value = Some(semaphoreData.toJson.prettyPrint)))
  
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
}

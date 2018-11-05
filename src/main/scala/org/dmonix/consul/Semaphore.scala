package org.dmonix.consul


import scala.concurrent.duration.DurationInt
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
  
  def release():Try[Boolean] = {
    consul
      .deleteKeyValue(memberFile) //delete our own member file
      .flatMap(_ => readLockData) //attempt to read lock data
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
      }
  }
  
  def tryAcquire():Try[Boolean] = {
    writeOwnMemberFile() //always write our own member/holder file
        .flatMap(_ => readLockData) //try to read the lock data
        .flatMap{ aggregatedData =>
          //if already a holder, return true
          if(aggregatedData.semaphoreData.isHolder(sessionID)) Success(true)
          //if there's enough permits left, try to take them
          else if(aggregatedData.semaphoreData.hasPermits) {
            //create new data with decreased permits and ourselves as holder
            val newData = aggregatedData.semaphoreData.decreasePermits().addHolder(sessionID)
            //attempt to write the updated lock data
            storeLockData(newData, aggregatedData.keyValue.modifyIndex)
              .flatMap{
                //data written, we got the lock/semaphore
                case true => Success(true)
                //failed to write, this is due to concurrent updates and the provided 'ModifyIndex' did not match, let's try again
                case false => tryAcquire()
              }
          }
          //not enough permits left, bail out  
          else Success(false)
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
  
  private def readLockData:Try[AggregatedData] = 
    consul
      .readKeyValue(lockFile)
      .flatMap{ 
        case Some(kv) => kv.value.map(_.parseJson.convertTo[SemaphoreData]) match {
          case Some(data) => Success(AggregatedData(kv, data))
          case None => Failure(new IllegalStateException(s"The data for path [$lockFile] has been erased"))
         }
        case None => Failure(new IllegalStateException(s"The path [$lockFile] no longer exists"))
      }
}

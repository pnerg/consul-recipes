package org.dmonix.consul

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Semaphore, TimeUnit}

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, pathPrefix, put, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.dmonix.consul.ConsulJsonProtocol._
import org.dmonix.consul.Implicits._
import org.slf4j.LoggerFactory
import spray.json._

import scala.collection._
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
object ConsulSim  {
  def apply() = new ConsulSim()
}

/**
  * Simulates Consul
  *
  * @author Peter Nerg
  */
class ConsulSim {
  private val logger = LoggerFactory.getLogger(classOf[ConsulSim])
  private val zeroDuration = 0.seconds
  private val defaultDuration = 5.seconds
  private case class Blocker(index:Int, semaphore: Semaphore) {
    def releaseIfIndexReached(mi:Int):Unit = if(mi >= index)semaphore.release()
  }

  private implicit val system = ActorSystem("consul-sim")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext = system.dispatcher

  private var server: Option[ServerBinding] = None

  private val creationCounter = new AtomicInteger(0)
  private val modificationCounter = new AtomicInteger(0)
  
  private val sessions = mutable.Map[String, Session]()
  private val keyValues = mutable.Map[String, KeyValue]()
  private val blockers = mutable.Map[String, Seq[Blocker]]()
  
  /**
    * Route for managing the various HTTP requests sent to the simulator
    */
  private val route: Route =
  /**
    * ==============================
    * v1/session
    * ==============================
    */
    pathPrefix("v1" / "session" ) {
      //create session
      pathPrefix("create") {
        put {
          val sessionID = UUID.randomUUID().toString
           val rsp = s"""
              |{
              | "ID": "$sessionID"
              |}
            """.stripMargin
          sessions.put(sessionID, Session())
          logger.debug(s"Created session [$sessionID]")
          complete(HttpEntity(ContentTypes.`application/json`, rsp))
        }
      } ~
      //destroy session
      pathPrefix("destroy" / Remaining)  { sessionID =>
        sessions.remove(sessionID).foreach(_ => logger.debug(s"Destroyed session [$sessionID]"))
        complete(HttpEntity(ContentTypes.`application/json`, "true"))
      } ~
      //renew session
      pathPrefix("renew" / Remaining)  { sessionID =>
        sessions.get(sessionID) match {
          case Some(session) => 
            //FIXME update the session data
            logger.debug(s"Renewed session [$sessionID]")
            complete(HttpEntity(ContentTypes.`application/json`, session.toJson.prettyPrint))
          case None => 
            complete(StatusCodes.NotFound, s"Session id '$sessionID' not found")
        }
      }
    } ~
    pathPrefix("v1" / "kv" / Remaining) { key =>
      put {
        parameters('cas?, 'acquire.?, 'release.?) { (cas, acquire, release) =>
          entity(as[Option[String]]) { entity =>
            (acquire, release) match {
              case (Some(id1), Some(id2)) => //both acquire and release are provided => illegal
                complete(StatusCodes.BadRequest, s"Conflicting flags: acquire=$id1&release=$id2")
              case (Some(id), None) if !sessionExists(id) => //acquire session does not exist
                complete(StatusCodes.InternalServerError, s"invalid session '$id'")
              case (None, Some(id)) if !sessionExists(id) => //release session does not exist
                complete(StatusCodes.InternalServerError, s"invalid session '$id'")
              case _ =>
                val kv = keyValues.getOrElse(key, KeyValue(createIndex = nextCreationIndex, modifyIndex = 0, lockIndex = 0, key = key, session = None, value = entity.filterNot(_.isEmpty)))
              val result = attemptSetKey(kv, cas.map(_.toInt), acquire, release)
                complete(HttpEntity(ContentTypes.`application/json`, result.toString))
            }
          }
        }
      } ~
      get {
        parameters('index?, 'wait.?) { (index, wait) =>
          val waitDuration = wait.map(_.asFiniteDuration).filterNot(_ == zeroDuration) getOrElse defaultDuration
          val modifyIndex = index.map(_.toInt) getOrElse 0
          readKey(key, modifyIndex, waitDuration) match {
            case Some(kv) =>
              complete(HttpEntity(ContentTypes.`application/json`, Seq(kv).toJson.prettyPrint))
            case None =>
              complete(StatusCodes.NotFound, s"No such key '$key'")
          }
        }
      }
    }  

  private def nextCreationIndex =  creationCounter.getAndIncrement()
  private def nextModificationIndex =  modificationCounter.getAndIncrement()
  
  private def sessionExists(sessionID:String):Boolean = sessions.contains(sessionID)
  private def attemptSetKey(kv: KeyValue, cas:Option[Int], acquire:Option[String], release:Option[String]): Boolean = {
    val passedCAS = cas.map(_ == kv.modifyIndex) getOrElse true
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
      keyValues.put(kv.key, kv)
      logger.debug(s"Storing key/value [$kv]")
      //notify any lock holders that the key has changed
      //we don't care to prune used Semaphores, sure this will leak objects but for a test rig it won't matter.
      //only release those that have a 'index' <= than the ModifyIndex on the key
      blockers.get(kv.key).foreach(_.foreach(_.releaseIfIndexReached(kv.modifyIndex)))
    }
    res.isDefined
  }

  private def readKey(key:String, index:Int, wait:FiniteDuration):Option[KeyValue] = {
    keyValues.get(key).flatMap{kv =>
      if(index <= kv.modifyIndex) 
        Some(kv)
      else {
        val semaphore = new Semaphore(0)
        val blocker = Seq(Blocker(index, semaphore))
        val seq = blockers.get(kv.key) map(_ ++ blocker) getOrElse blocker
        blockers.put(kv.key, seq)
        //hold here until the time runs out of someone updates the key
        semaphore.tryAcquire(wait.toMillis, TimeUnit.MILLISECONDS) 
        readKey(key, 0, zeroDuration)
      }
    }
  }
  
  def start(port:Int = 0): ConsulHost = synchronized {
    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", port)
    val binding =  Await.result(bindingFuture, 10.seconds)
    server = Some(binding)
    ConsulHost("localhost", binding.localAddress.getPort)
  }

  def shutdown(): Terminated = synchronized {
    val shutdownFuture = server
      .map(_.unbind()) //unbind the server if it is started
      .getOrElse(Future.successful(())) //server not started, shutdown is "success"
      .flatMap(_ => system.terminate()) //terminate the actor system

    Await.result(shutdownFuture, 30.seconds)
  }

  def consulHost:Option[ConsulHost] = server.map(b => ConsulHost("localhost", b.localAddress.getPort))
}



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

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
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
import scala.util.Try

object ConsulSim {
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

  private implicit val system = ActorSystem("consul-sim")
  private implicit val materializer = ActorMaterializer()
  private implicit val executionContext = system.dispatcher

  private var server: Option[ServerBinding] = None

  /** The internal session storage */
  val sessionStorage = SessionStorage()

  /** The internal key/value storage */
  val keyValueStorage = KeyValueStorage()

  /**
    * Starts the simulator on the designated port
    * @param port The port to listen to, defaults to 0 i.e. chosen by the host
    * @return The host/port the simulator listens to
    */
  def start(port: Int = 0): ConsulHost = synchronized {
    val bindingFuture = Http().bindAndHandle(sessionRoute ~ keyValueRoute, "0.0.0.0", port)
    val binding = Await.result(bindingFuture, 10.seconds)
    server = Some(binding)
    logger.info(s"Started Consul Sim on port [${binding.localAddress.getPort}]")
    ConsulHost("localhost", binding.localAddress.getPort)
  }

  /**
    * Shutdown the simulator
    * @param maxWait Maximum wait time for the simulator to properly stop
    * @return
    */
  def shutdown(maxWait: FiniteDuration = 30.seconds): Try[Done] = synchronized {
    Try(Await.result(shutdownNonBlocking(), maxWait))
  }

  /**
    * Shutdown the simulator
    * @return
    */
  def shutdownNonBlocking(): Future[Done] = synchronized {
    val shutdownFuture = server
      .map { binding =>
        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
          "shutdown-connection-pools"
        ) { () =>
          Http.get(system).shutdownAllConnectionPools().map(_ => Done)
        }
        CoordinatedShutdown(system).run(CoordinatedShutdown.unknownReason)
      }
      .getOrElse(Future.successful(Done))
    server = None
    shutdownFuture
  }

  /**
    * Returns the host/port the simulator listens to if started
    * @return
    */
  def consulHost: Option[ConsulHost] = server.map(b => ConsulHost("localhost", b.localAddress.getPort))

  /**
    * ==============================
    * Route for managing the various session related requests sent to the simulator
    * v1/session
    * ==============================
    */
  private[consul] val sessionRoute: Route =
    pathPrefix("v1" / "session") {
      //create session
      pathPrefix("create") {
        put {
          //easier to debug/trace logs with a sequential counter as sessionID generator
          val sessionID = sessionStorage.createSession()
          val rsp = s"""
                       |{
                       | "ID": "$sessionID"
                       |}
            """.stripMargin
          logger.debug(s"Created session [$sessionID]")
          complete(HttpEntity(ContentTypes.`application/json`, rsp))
        }
      } ~
        //destroy session
        pathPrefix("destroy" / Remaining) { sessionID =>
          sessionStorage.removeSession(sessionID).foreach(_ => logger.debug(s"Destroyed session [$sessionID]"))
          complete(HttpEntity(ContentTypes.`application/json`, "true"))
        } ~
        //renew session
        pathPrefix("renew" / Remaining) { sessionID =>
          sessionStorage.getSession(sessionID) match {
            case Some(session) =>
              //FIXME update the session data
              logger.debug(s"Renewed session [$sessionID]")
              complete(HttpEntity(ContentTypes.`application/json`, session.toJson.prettyPrint))
            case None =>
              complete(StatusCodes.NotFound, s"Session id '$sessionID' not found")
          }
        }
    }

  /**
    * ==============================
    * Route for managing the various key/value requests sent to the simulator
    * v1/kv
    * ==============================
    */
  private[consul] val keyValueRoute: Route =
    pathPrefix("v1" / "kv" / Remaining) { key =>
      //store kv
      put {
        parameters('cas.?, 'acquire.?, 'release.?, 'flags.?) { (cas, acquire, release, flags) =>
          entity(as[Option[String]]) { entity =>
            (acquire, release) match {
              case (Some(id1), Some(id2)) => //both acquire and release are provided => illegal
                complete(StatusCodes.BadRequest, s"Conflicting flags: acquire=$id1&release=$id2")
              case (Some(id), None) if !sessionStorage.sessionExists(id) => //acquire session does not exist
                complete(StatusCodes.InternalServerError, s"invalid session '$id'")
              case (None, Some(id)) if !sessionStorage.sessionExists(id) => //release session does not exist
                complete(StatusCodes.InternalServerError, s"invalid session '$id'")
              case _ =>
                val newValue = entity.filterNot(_.isEmpty)
                val result =
                  keyValueStorage.createOrUpdate(key, newValue, cas.map(_.toInt), acquire, release, flags.map(_.toInt))
                complete(HttpEntity(ContentTypes.`application/json`, result.toString))
            }
          }
        }
      } ~
        //read kv
        get {
          parameters('index.?, 'wait.?, 'recurse.?) { (index, wait, recurse) =>
            val waitDuration = wait.map(_.asFiniteDuration).filterNot(_ == zeroDuration) getOrElse defaultDuration
            val modifyIndex = index.map(_.toInt) getOrElse 0
            logger.debug(
              s"Attempting to read [$key] with index [$modifyIndex] wait [$waitDuration] and recurse [$recurse]"
            )
            keyValueStorage.readKey(key, modifyIndex, waitDuration) match {
              //non-recursive call return the found key
              case Some(kv) if recurse.isEmpty =>
                logger.debug(s"Read data for [$key] [$kv]")
                complete(HttpEntity(ContentTypes.`application/json`, Seq(kv).toJson.prettyPrint))
              //recursive call, return all keys on the requested path
              case _ if recurse.isDefined =>
                val res = keyValueStorage.getKeysForPath(key)
                logger.debug(s"Recursively read [$key] found [${res.size}] keys with [$res]")
                complete(HttpEntity(ContentTypes.`application/json`, res.toJson.prettyPrint))
              //no such key
              case _ =>
                complete(StatusCodes.NotFound, s"No such key '$key'")
            }
          }
        } ~
        //delete kv
        delete {
          parameters('cas.?, 'recurse.?) { (cas, recurse) =>
            val recursive = recurse getOrElse false //TODO implement recursive delete
            keyValueStorage.removeKey(key)
            complete(HttpEntity(ContentTypes.`application/json`, "true"))
          }
        }
    }
}

package org.dmonix.consul

import java.util.concurrent.TimeUnit

import spray.json._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * Encapsulates functions for communication towards Consul.
  * @author Peter Nerg
  */
trait Consul {
  private[consul] val zeroDuration = FiniteDuration(0, TimeUnit.SECONDS)
  
  /**
    * Attempts to create a session in Consul.
    * @param session Data to store on the session
    * @return The created session id
    */
  def createSession(session:Session):Try[SessionID]

  /**
    * Attempts to destroy a session in Consul.
    * This function is idempotent, i.e. it will not fail even if the session does not exist
    * @param sessionID The session to destroy
    * @return
    */
  def destroySession(sessionID:SessionID):Try[Unit]

  /**
    * Attempts to store a value on the provided key
    * @param key The full path of the key, e.g foo/bar/my-config
    * @param value The value to store on the key
    * @return
    */
  def storeKeyValue(key:String, value:String):Try[Boolean] = storeKeyValue(SetKeyValue(key, Option(value)))
  
  def storeKeyValue(kv:SetKeyValue):Try[Boolean] 
  
  /**
    * Blocks and waits for provided key to changed value.  
    * This is done by waiting until the ''ModifyIndex'' on the key has gone passed the provided ''modifyIndex''.  
    * If the provided index is lower than what is represented in Consul this function returns immediately.
    * The function always returns the value of the key even if the provided wait time has been exceeded.
    * @param key The full path of the key, e.g foo/bar/my-config
    * @param modifyIndex The modification index value to block on
    * @param maxWait Max wait time
    * @return
    */
  def readKeyValueWhenChanged(key:String, modifyIndex:Int, maxWait:FiniteDuration):Try[Option[KeyValue]]

  /**
    * Attempts to read the value for the provided key.
    * @param key The full path of the key, e.g foo/bar/my-config
    * @return ''Success'' if managed to access Consul, then ''Some'' if the value was found, ''None'' else
    */
  def readKeyValue(key:String):Try[Option[KeyValue]] = readKeyValueWhenChanged(key, 0, zeroDuration)
}

object Consul {

  /**
    * Creates a an instance to communicate towards Consul
    * @param consulHost
    * @return
    */
  def apply(consulHost: ConsulHost):Consul = new ConsulImpl(new ConsulHttpSender(consulHost)) 
}

private[consul] class ConsulImpl(httpSender:HttpSender) extends Consul {
  import ConsulJsonProtocol._
  import Implicits._

  
  override def createSession(session:Session):Try[SessionID] =
    httpSender
      .put("/session/create")
      .asJson
      .map(_.fieldValOrFail[String]("ID"))


  override def destroySession(sessionID:SessionID):Try[Unit] = 
    httpSender
      .put(s"/session/destroy/$sessionID")
      .asUnit

  override def storeKeyValue(kv:SetKeyValue):Try[Boolean] = {
    val params = Seq(
      kv.compareAndSet.map("cas="+_),
      kv.acquire.map("acquire="+_),
      kv.release.map("release="+_)
    ).flatten.mkString("?", "&", "")
    
    httpSender
      .put(s"/kv/${kv.key}"+params, kv.value)
      .map(_.toBoolean)
  }
  
  override def readKeyValueWhenChanged(key:String, modifyIndex:Int, maxWait:FiniteDuration):Try[Option[KeyValue]] =
    httpSender
      .get(s"/kv/$key?index=$modifyIndex&wait=${maxWait.toSeconds}s")
      .map(_.flatMap{
        _.parseJson match {
          case JsArray(data) => data.headOption.mapTo[KeyValue]
          case _ => deserializationError(s"Got unexpected response for key [$key]")
        }
      })

}

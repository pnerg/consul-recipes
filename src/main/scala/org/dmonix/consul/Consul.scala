package org.dmonix.consul

import spray.json._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Try

/**
  * Companion object to ''Consul''
  */
object Consul {
  private[consul] val zeroDuration = 0.seconds

  /**
    * Creates a an instance to communicate towards Consul
    * @param consulHost
    * @return
    */
  def apply(consulHost: ConsulHost):Consul = new Consul(new ConsulHttpSender(consulHost)) 
}

import org.dmonix.consul.Consul._
/**
  * Encapsulates functions for communication towards Consul.
  * @author Peter Nerg
  */
class Consul(httpSender:HttpSender) {
  import ConsulJsonProtocol._
  import Implicits._

  /**
    * Attempts to create a session in Consul.
    * @param session Data to store on the session
    * @return The created session id
    */
  def createSession(session:Session):Try[SessionID] =
    httpSender
      .put("/session/create", session.data)
      .asJson
      .map(_.fieldValOrFail[String]("ID"))

  /**
    * Attempts to destroy a session in Consul.
    * This function is idempotent, i.e. it will not fail even if the session does not exist
    * @param sessionID The session to destroy
    * @return
    */
  def destroySession(sessionID:SessionID):Try[Unit] = 
    httpSender
      .put(s"/session/destroy/$sessionID")
      .asUnit

  /**
    * Attempts to renew a session in Consul.
    * @param sessionID The session to renew
    * @return The session data
    */  
  def renewSession(sessionID:SessionID):Try[Session] =
    httpSender
      .put(s"/session/renew/$sessionID")
      .map(_.parseJson)
      .map(_.convertTo[Session])

  /**
    * Attempts to store a value on the provided key.  
    * This function will always write to the key irrespective if there is an owning session.  
    * @param key The full path of the key, e.g foo/bar/my-config
    * @param value The optional value to store on the key
    * @return ''Success'' if managed to access Consul, then true id the key/value was set 
    */
  def storeKeyValue(key:String, value:Option[String]):Try[Boolean] = storeKeyValue(SetKeyValue(key = key, value = value))

  /**
    * Attempts to store a value on the provided key only if the key did not previously exist.  
    * @param key The full path of the key, e.g foo/bar/my-config
    * @param value The optional value to store on the key
    * @return ''Success'' if managed to access Consul, then true id the key/value was set 
    */
  def storeKeyValueIfNotSet(key:String, value:Option[String]):Try[Boolean] = storeKeyValue(SetKeyValue(key = key, value = value, compareAndSet = Some(0)))

  /**
    * Attempts to store a value on the provided key.  
    * The exact behavior of the storage operation is determined by the values set on the provided ''SetKeyValue''
    * @param kv The key value data
    * @return ''Success'' if managed to access Consul, then true id the key/value was set 
    */
  def storeKeyValue(kv:SetKeyValue):Try[Boolean] = {
    val params = Map(
      "cas" -> kv.compareAndSet,
      "acquire" -> kv.acquire,
      "release" -> kv.release
    ).asURLParams

    httpSender
      .put(s"/kv/${kv.key}"+params, kv.value)
      .map(_.toBoolean)
  }

  /**
    * Attempts to read the value for the provided key.
    * @param key The full path of the key, e.g foo/bar/my-config
    * @return ''Success'' if managed to access Consul, then ''Some'' if the value was found, ''None'' else
    */
  def readKeyValue(key:String):Try[Option[KeyValue]] = readKeyValueWhenChanged(key, 0, zeroDuration)

  /**
    * Blocks and waits for provided key to changed value.  
    * This is done by waiting until the ''ModifyIndex'' on the key has gone passed the provided ''modifyIndex''.  
    * If the provided index is lower than what is represented in Consul this function returns immediately.
    * The function always returns the value of the key even if the provided wait time has been exceeded.
    * @param key The full path of the key, e.g foo/bar/my-config
    * @param modifyIndex The modification index value to block on
    * @param maxWait Max wait time
    * @return ''Success'' if managed to access Consul, then ''Some'' if the value was found, ''None'' else
    */
  def readKeyValueWhenChanged(key:String, modifyIndex:Int, maxWait:FiniteDuration):Try[Option[KeyValue]] =
    httpSender
      .get(s"/kv/$key?index=$modifyIndex&wait=${maxWait.toSeconds}s")
      .map(_.flatMap{
        _.parseJson match {
          case JsArray(data) => data.headOption.mapTo[KeyValue]
          case _ => deserializationError(s"Got unexpected response for key [$key]")
        }
      })

  /**
    * Deletes the provided key
    * This function is idempotent, i.e. it will not fail even if the key does not exist
    * @param key The key to remove
    * @return ''Success'' if managed to access Consul, then normally 'true'
    */
  def deleteKeyValue(key:String):Try[Boolean] = deleteKeyValue(DeleteKeyValue(key = key))

  /**
    * Deletes the provided key
    * This function is idempotent, i.e. it will not fail even if the key does not exist
    * @param kv The key to remove
    * @return ''Success'' if managed to access Consul, then normally 'true'
    */
  def deleteKeyValue(kv: DeleteKeyValue):Try[Boolean] = {
    val params = Map(
      "cas" -> kv.compareAndSet,
      "recurse" -> Option(kv.recursive)
    ).asURLParams

    httpSender
      .delete("/kv/"+kv.key+params)
      .map(_.toBoolean)
  }
}

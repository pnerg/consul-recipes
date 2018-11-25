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
  private[consul] implicit class RichSearchResult(t:Try[Option[Stream[KeyValue]]]) {
    def firstKeyOpt:Try[Option[KeyValue]] = t.map(_.flatMap(_.headOption))
  }
}

import org.dmonix.consul.Consul._
/**
  * Encapsulates functions towards Consul.
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
      .put("/session/create", Some(session.toJson.prettyPrint))
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
  def readKeyValue(key:String):Try[Option[KeyValue]] = readKeyValues(GetKeyValue(key)).firstKeyOpt

  /**
    * Attempts to recursively read the key/values for the provided key path
    * @param key The path to query
    * @return ''Success'' if managed to access Consul, then ''Some'' if the key was found followed by the stream of key/values matching the query
    */
  def readKeyValueRecursive(key:String):Try[Option[Stream[KeyValue]]] = readKeyValues(GetKeyValue(key, None, None, true))
  
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
  def readKeyValueWhenChanged(key:String, modifyIndex:Int, maxWait:FiniteDuration):Try[Option[KeyValue]] = readKeyValues(GetKeyValue(key, Some(modifyIndex), Option(maxWait))).firstKeyOpt

  /**
    * Attempts to read the key/value(s) as specified by the provided data.
    * The result is a stream since if ''recursive'' is requested then there could be more than one key returned
    * @param kv The key to query
    * @return ''Success'' if managed to access Consul, then ''Some'' if the key was found followed by the stream of key/values matching the query
    */
  def readKeyValues(kv: GetKeyValue):Try[Option[Stream[KeyValue]]] = {
    val params = Map(
      "index" -> kv.modifyIndex,
      "wait" -> kv.maxWait.map(_.toMillis+"ms"),
      "recurse" -> (if(kv.recursive) Some(true) else None)
    ).asURLParams

    httpSender
      .get(s"/kv/"+kv.key+params)
      .map(_.map{
        _.parseJson match {
          case JsArray(data) => data.toStream.map(_.convertTo[KeyValue])
          case _ => deserializationError(s"Got unexpected response for key [${kv.key}]")
        }
      })
  }

  /**
    * Deletes the provided key
    * This function is idempotent, i.e. it will not fail even if the key does not exist
    * @param key The key to remove
    * @return ''Success'' if managed to access Consul, then normally 'true'
    */
  def deleteKeyValue(key:String):Try[Boolean] = deleteKeyValue(DeleteKeyValue(key = key))

  /**
    * Recursively deletes the provided key and all its descendants/children.
    * This function is idempotent, i.e. it will not fail even if the key does not exist
    * @param key The key to remove
    * @return ''Success'' if managed to access Consul, then normally 'true'
    */
  def deleteKeyValueRecursive(key:String):Try[Boolean] = deleteKeyValue(DeleteKeyValue(key = key, recursive = true))

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

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

import java.util.Base64

import org.dmonix.consul.Implicits._
import spray.json._

import scala.collection.immutable.ListMap
import scala.concurrent.duration.FiniteDuration

/**
  * @author Peter Nerg
  */
private[consul] object ConsulJsonProtocol extends DefaultJsonProtocol {
  
  private def nonEmptyString(s:String):Boolean = Option(s).map(!_.isEmpty) getOrElse false

  /**
    * Formater to convert ''FiniteDuration'' to/from Json
    */
  implicit object FiniteDurationFormat extends RootJsonFormat[FiniteDuration] {

    override def write(obj: FiniteDuration): JsValue = JsString(obj.toSeconds+"s")

    override def read(json: JsValue): FiniteDuration = json.convertTo[String].asFiniteDuration
  }
  
  implicit object SessionFormat extends RootJsonFormat[Session] {
    override def write(obj: Session): JsValue = {
      val builder = ListMap.newBuilder[String, JsValue]
      obj.name.foreach(builder += "Name" -> _.toJson)
      obj.lockDelay.foreach(builder += "LockDelay" -> _.toJson)
      obj.node.foreach(builder += "Node" -> _.toJson)
      obj.behavior.foreach(builder += "Behavior" -> _.toJson)
      obj.ttl.foreach(builder += "TTL" -> _.toJson)
      JsObject(builder.result())
    }

    override def read(json: JsValue): Session = {
      Session(
        name = json.fieldVal[String]("Name").filter(nonEmptyString),
        lockDelay = json.fieldVal[FiniteDuration]("LockDelay"),
        node = json.fieldVal[String]("Node").filter(nonEmptyString),
        behavior = json.fieldVal[String]("Behavior").filter(nonEmptyString),
        ttl = json.fieldVal[FiniteDuration]("TTL")
      )
    }
  }

  implicit object KeyValueFormat extends RootJsonFormat[KeyValue] {
    val charset = "UTF-8"
    def encode(s:String):String = new String(Base64.getEncoder.encode(s.getBytes(charset)), charset)
    override def write(obj: KeyValue): JsValue = {
      val builder = ListMap.newBuilder[String, JsValue]
      builder += "CreateIndex" -> obj.createIndex.toJson
      builder += "ModifyIndex" -> obj.modifyIndex.toJson
      builder += "LockIndex" -> obj.lockIndex.toJson
      builder += "Flags" -> obj.flags.toJson
      builder += "Key" -> obj.key.toJson
      obj.value.map(encode).foreach(builder += "Value" -> _.toJson)
      obj.session.foreach(builder += "Session" -> _.toJson)
      JsObject(builder.result())
    }

    override def read(json: JsValue): KeyValue = {
      def decode(s:String):String = new String(Base64.getDecoder.decode(s), charset) 
      KeyValue(
        createIndex = json.fieldValOrFail[Int]("CreateIndex"),
        modifyIndex = json.fieldValOrFail[Int]("ModifyIndex"),
        lockIndex = json.fieldValOrFail[Int]("LockIndex"),
        flags = json.fieldValOrFail[Int]("Flags"),
        key = json.fieldValOrFail[String]("Key"),
        value = json.fieldVal[String]("Value").map(decode),
        session = json.fieldVal[String]("Session")
      )
    }
  }


  implicit object SemaphoreDataFormat extends RootJsonFormat[SemaphoreData] {
    override def write(obj: SemaphoreData): JsValue = {
      val builder = ListMap.newBuilder[String, JsValue]
      builder += "Permits" -> obj.permits.toJson
      builder += "Holders" -> obj.holders.toJson
      JsObject(builder.result())
    }

    override def read(json: JsValue): SemaphoreData = {
      SemaphoreData(
        permits = json.fieldValOrFail[Int]("Permits"),
        holders = json.fieldValOrFail[Set[SessionID]]("Holders")
      )
    }
  }
}

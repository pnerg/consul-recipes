package org.dmonix.consul

import java.util.Base64

import spray.json._

import scala.collection.immutable.ListMap
import Implicits._

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * @author Peter Nerg
  */
object ConsulJsonProtocol extends DefaultJsonProtocol {
  
  private def nonEmptyString(s:String):Boolean = !s.isEmpty

  /**
    * Formater to convert ''FiniteDuration'' to/from Json
    */
  implicit object FiniteDurationFormat extends RootJsonFormat[FiniteDuration] {

    override def write(obj: FiniteDuration): JsValue = {
      JsString(obj.toSeconds+"s")
    }

    override def read(json: JsValue): FiniteDuration = {
      Duration(json.convertTo[String]) match {
        case d:FiniteDuration => d
        case _ => deserializationError(s"The string [${json.convertTo[String]}] is not a valid FiniteDuration")
      }
    }
  }
  
  implicit object SessionFormat extends RootJsonFormat[Session] {
    override def write(obj: Session): JsValue = {
      val builder = ListMap.newBuilder[String, JsValue]
      obj.name.foreach(builder += "Name" -> _.toJson)
      obj.lockDelay.foreach(builder += "LockDelay" -> _.toJson)
      obj.node.foreach(builder += "Node" -> _.toJson)
      obj.behavior.foreach(builder += "Behavior" -> _.toJson)
      //obj.checks.foreach(builder += "Checks" -> _.toJson)
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
    override def write(obj: KeyValue): JsValue = serializationError("Serialization of [KeyValue] is not supported")

    override def read(json: JsValue): KeyValue = {
      def decode(s:String):String = new String(Base64.getDecoder.decode(s), "UTF-8") 
      KeyValue(
        createIndex = json.fieldValOrFail[Int]("CreateIndex"),
        modifyIndex = json.fieldValOrFail[Int]("ModifyIndex"),
        lockIndex = json.fieldValOrFail[Int]("LockIndex"),
        key = json.fieldValOrFail[String]("Key"),
        value = json.fieldVal[String]("Value").map(decode),
        session = json.fieldVal[String]("Session")
      )
    }
  }
}

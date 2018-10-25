package org.dmonix.consul

import java.util.Base64

import spray.json._

import scala.collection.immutable.ListMap
import Implicits._

/**
  * @author Peter Nerg
  */
object ConsulJsonProtocol extends DefaultJsonProtocol {
  
  implicit object SessionFormat extends RootJsonFormat[Session] {
    override def write(obj: Session): JsValue = {
      val builder = ListMap.newBuilder[String, JsValue]
      builder += "Name" -> obj.name.toJson
      obj.lockDelay.map(_+"s").foreach(builder += "LockDelay" -> _.toJson)
      obj.node.foreach(builder += "Node" -> _.toJson)
      obj.behavior.foreach(builder += "Behavior" -> _.toJson)
      //obj.checks.foreach(builder += "Checks" -> _.toJson)
      obj.ttl.map(_+"s").foreach(builder += "TTL" -> _.toJson)
      JsObject(builder.result())
    }

    override def read(json: JsValue): Session = sys.error("Deserialization of [Session] is unsupported")
  }

  implicit object KeyValueFormat extends RootJsonFormat[KeyValue] {
    override def write(obj: KeyValue): JsValue = sys.error("Serialization of [KeyValue] is not supported")

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

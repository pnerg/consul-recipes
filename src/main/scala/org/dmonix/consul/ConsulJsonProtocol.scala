package org.dmonix.consul

import java.util.Base64

import org.dmonix.consul.Implicits._
import spray.json._

import scala.collection.immutable.ListMap
import scala.concurrent.duration.FiniteDuration

/**
  * @author Peter Nerg
  */
object ConsulJsonProtocol extends DefaultJsonProtocol {
  
  private def nonEmptyString(s:String):Boolean = !s.isEmpty

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
    val charset = "UTF-8"
    def encode(s:String):String = new String(Base64.getEncoder.encode(s.getBytes(charset)), charset)
    override def write(obj: KeyValue): JsValue = {
      val builder = ListMap.newBuilder[String, JsValue]
      builder += "CreateIndex" -> obj.createIndex.toJson
      builder += "ModifyIndex" -> obj.modifyIndex.toJson
      builder += "LockIndex" -> obj.lockIndex.toJson
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
        key = json.fieldValOrFail[String]("Key"),
        value = json.fieldVal[String]("Value").map(decode),
        session = json.fieldVal[String]("Session")
      )
    }
  }
}

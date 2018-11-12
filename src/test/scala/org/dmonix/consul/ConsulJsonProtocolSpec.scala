package org.dmonix.consul

import java.util.Base64

import org.dmonix.consul.ConsulJsonProtocol._
import org.specs2.mutable.Specification
import spray.json._

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
  * @author Peter Nerg
  */
class ConsulJsonProtocolSpec extends Specification {

  "Converting FiniteDuration" >> {
    val duration = 3.minutes
    "to Json shall be successful" >> {
      duration.toJson === JsString("180s")
    }
    "from Json shall be successful" >> {
      JsString("180s").convertTo[FiniteDuration] === duration
    }
    "shall be able to round-trip object -> json -> string -> json -> object" >> {
      duration
        .toJson
        .prettyPrint
        .parseJson
        .convertTo[FiniteDuration] === duration
    }
  }
  
  "Converting Session" >> {
    val session = Session(
      name = Some("my-name"), 
      lockDelay = Some(69.seconds),
      node = Some("some-node"),
      behavior = Some("release"),
      ttl = Some(69.minutes)
    )
    val json =
      """ 
        | {
        |   "Name": "my-name",
        |   "LockDelay": "69s",
        |   "Node": "some-node",
        |   "Behavior": "release",
        |   "TTL": "4140s"
        | }
      """.stripMargin.parseJson
    
    "to Json shall be successful" >> {
      session.toJson === json
    }
    "from Json shall be successful" >> {
      json.convertTo[Session] === session
    }
    "shall be able to round-trip object -> json -> string -> json -> object" >> {
      session
        .toJson
        .prettyPrint
        .parseJson
        .convertTo[Session] === session
    }
    
  }
  
  "Converting KeyValue" >> {
    val data = "Hello World!!!"
    val encoded = new String(Base64.getEncoder.encode(data.getBytes("UTF-8")), "UTF-8")
    val json = s"""
                 |{
                 |  "CreateIndex": 100,
                 |  "ModifyIndex": 200,
                 |  "LockIndex": 69,
                 |  "Key": "my-key",
                 |  "Flags": 0,
                 |  "Value": "$encoded",
                 |  "Session": "adf4238a-882b-9ddc-4a9d-5b6758e4159e"
                 |}
                 |""".stripMargin.parseJson

    "from Json shall be successful" >> {
      json.convertTo[KeyValue] === KeyValue(
        createIndex = 100,
        modifyIndex = 200,
        lockIndex = 69,
        key = "my-key",
        value = Some(data),
        session = Some("adf4238a-882b-9ddc-4a9d-5b6758e4159e")
      )
    }
  }
}

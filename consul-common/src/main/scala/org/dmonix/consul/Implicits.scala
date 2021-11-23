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

import scala.collection.immutable.List
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * @author Peter Nerg
  */
object Implicits {

  implicit class RichString(s: String) {

    /**
      * Attempts to parse a ''FiniteDuration'' out of the string
      * @return
      */
    def asFiniteDuration = Duration(s) match {
      case d: FiniteDuration => d
      case _                 => deserializationError(s"The string [$s] is not a valid FiniteDuration")
    }
  }

  implicit class RichFuture[T](t: Future[Try[T]]) {
    def flatten(implicit ec: ExecutionContext): Future[T] = t.map(_.get)
  }

  implicit class RichJSOption(value: Option[JsValue]) {
    def convertTo[T: JsonReader](default: => T): T =
      value
        .map {
          case JsNull => default
          case s      => s.convertTo[T]
        }
        .getOrElse(default)

    /**
      * Attempts to convert the value in the ''Option'' to some provided type.
      * Similar to [[convertTo]] but without any default values.
      * @tparam T
      * @return The mapped Option
      */
    def mapTo[T: JsonReader]: Option[T] = value
      .filter(_ match { // The filter is to get rid of JsNull values
        case JsNull => false
        case _      => true
      })
      .map(_.convertTo[T]) // convert to desired type
  }

  /**
    * Pimps a JsValue with some nice helper functions
    * @param value
    */
  implicit class RichJSValue(value: JsValue) {

    /**
      * Attempts to retrieve the named field on the Json object and convert it to desired type.
      * @param name The name of the field
      * @tparam T The typ to convert to
      * @return Optional value if the field was found
      */
    def fieldVal[T: JsonReader](name: String): Option[T] = value.asJsObject.fields.get(name).mapTo[T]

    /**
      * Attempts to retrieve the named field on the Json object and convert it to desired type.
      * @param name The name of the field
      * @param default The fallback value to return if the field was not found
      * @tparam T The typ to convert to
      * @return Optional value if the field was found
      */
    def fieldVal[T: JsonReader](name: String, default: T): T = fieldVal[T](name).getOrElse(default)

    /**
      * Attempts to retrieve the named field on the Json object and convert it to desired type.
      * The function will fail if the field does not exist
      * @param name The name of the field
      * @tparam T The typ to convert to
      * @return The value of the field, or an exception if missing
      */
    def fieldValOrFail[T: JsonReader](name: String): T =
      fieldVal[T](name).getOrElse(deserializationError(s"Missing field [$name]", null, List(name)))

    def convertToOrDefault[T: JsonReader](default: => T) =
      value match {
        case JsNull => default
        case s      => s.convertTo[T]
      }
  }

  implicit class RichTry(t: Try[String]) {
    def asJson: Try[JsValue] = t.map(JsonParser(_))
    def mapTo[T: JsonReader](): T = asJson.map(_.convertTo[T]) match { // convert to desired type
      case Success(x)  => x
      case Failure(ex) => throw ex
    }

    def asUnit(): Try[Unit] = t.map(_ => ())
  }

  implicit class RichOption[T](o: Option[T]) {
    def asTry(): Try[T] = o.map(Success(_)) getOrElse (Failure(new NoSuchElementException("Empty Option")))
  }

  implicit class RichOptionMap(map: Map[String, Option[Any]]) {

    /**
      * Converts the Map of key/Option into a URL param string (''?foo=bar&arg=2''
      * @return String with the params, empty string if empty list or all items are None
      */
    def asURLParams: String = map.collect { case (key, Some(value)) => key + "=" + value } match {
      case Nil => "" // empty seq => empty string, .mkstring would else always add '?' even if the seq is empty
      case seq => seq.mkString("?", "&", "")
    }
  }

}

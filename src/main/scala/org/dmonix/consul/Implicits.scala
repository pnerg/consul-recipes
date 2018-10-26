package org.dmonix.consul

import spray.json._

import scala.collection.immutable.List
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * @author Peter Nerg
  */
object Implicits {
  
  implicit class  RichFuture[T](t:Future[Try[T]]) {
    def flatten(implicit ec:ExecutionContext):Future[T] = t.map(_.get)
  }

  implicit class RichJSOption(value: Option[JsValue]) {
    def convertTo[T: JsonReader](default: => T):T =
      value.map {
        case JsNull => default
        case s      => s.convertTo[T]
      }.getOrElse(default)

    /**
      * Attempts to convert the value in the ''Option'' to some provided type.
      * Similar to [[convertTo]] but without any default values.
      * @tparam T
      * @return The mapped Option
      */
    def mapTo[T :JsonReader] : Option[T] = value.filter(_ match  { //The filter is to get rid of JsNull values
      case JsNull => false
      case _ => true
    }).map(_.convertTo[T]) //convert to desired type
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
    def fieldVal[T: JsonReader](name:String):Option[T] = value.asJsObject.fields.get(name).mapTo[T]

    /**
      * Attempts to retrieve the named field on the Json object and convert it to desired type.
      * @param name The name of the field
      * @param default The fallback value to return if the field was not found
      * @tparam T The typ to convert to
      * @return Optional value if the field was found
      */
    def fieldVal[T: JsonReader](name:String, default:T):T = fieldVal[T](name).getOrElse(default)

    /**
      * Attempts to retrieve the named field on the Json object and convert it to desired type.
      * The function will fail if the field does not exist
      * @param name The name of the field
      * @tparam T The typ to convert to
      * @return The value of the field, or an exception if missing
      */
    def fieldValOrFail[T: JsonReader](name:String):T = fieldVal[T](name).getOrElse(deserializationError(s"Missing field [$name]", null, List(name)))

    def convertToOrDefault[T: JsonReader](default: => T) =
      value match {
        case JsNull => default
        case s      => s.convertTo[T]
      }
  }

  implicit class RichTry(t:Try[String]) {
    def asJson:Try[JsValue] = t.map(JsonParser(_))
    def mapTo[T :JsonReader]() : T = asJson.map(_.convertTo[T]) match { //convert to desired type
      case Success(x) => x
      case Failure(ex) => throw ex
    }
    
    def asUnit():Try[Unit] = t.map(_ => ())
  }



}

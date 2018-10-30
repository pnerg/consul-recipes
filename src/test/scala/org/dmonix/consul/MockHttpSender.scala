package org.dmonix.consul

import java.io.IOException

import scala.util.{Failure, Success, Try}

trait MockResponses {
  import spray.json._
  import ConsulJsonProtocol._
  
  def failureResponse = Failure(new IOException(s"Got unexpected response [666][Shit hit the fan"))
  def sessionCreatedJson(sessionID:String = "12345") =  s"""
                                  |{
                                  | "ID": "$sessionID"
                                  |}""".stripMargin

  def sessionResponse(session:Session) = Success(session.toJson.prettyPrint)
}

trait MockHttpSender {
  private def notImplemented[T]:PartialFunction[Any, Try[T]] = {case _ => Failure(new NotImplementedError("The function is not supported"))}

  def sessionCreatedResponse(sessionID:String = "12345") = Success(
    s"""
      |{
      | "ID": "$sessionID"
      |} 
    """.stripMargin)
  def mockGet(pf: PartialFunction[String, Try[Option[String]]]): HttpSender = new MockHttpSenderImpl(notImplemented, pf)
  def mockPut(pf: PartialFunction[(String, Option[String]), Try[String]]): HttpSender = new MockHttpSenderImpl(pf, notImplemented)
}

/**
  * @author Peter Nerg
  */
class MockHttpSenderImpl(putResponse: PartialFunction[(String, Option[String]), Try[String]], getResponse: PartialFunction[String, Try[Option[String]]]) extends HttpSender {
  private def noMatch[T] = (_:Any) => Failure[T](new MatchError("Request data did not match provided function"))
  def put(path:String, body:Option[String] = None):Try[String] = putResponse.applyOrElse((path, body), noMatch)
  def get(path:String):Try[Option[String]] = getResponse.applyOrElse(path, noMatch)
}

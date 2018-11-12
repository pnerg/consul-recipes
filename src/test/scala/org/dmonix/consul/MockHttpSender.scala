package org.dmonix.consul

import java.io.IOException

import org.dmonix.consul.types.{DeleteResponseFunc, GetResponseFunc, PutResponseFunc}

import scala.util.{Failure, Success, Try}

trait MockResponses {
  import spray.json._
  import ConsulJsonProtocol._
  
  def failureResponse = Failure(new IOException(s"Got unexpected response [666][Shit hit the fan"))
  def getKVResponse(data:Option[String] = None) = ??? //Success(new KeyValue())
  def trueResponse = Success("true")
  def falseResponse = Success("false")
  def sessionCreatedJson(sessionID:String = "12345") =  s"""
                                  |{
                                  | "ID": "$sessionID"
                                  |}""".stripMargin

  def sessionResponse(session:Session) = Success(session.toJson.prettyPrint)
}
object types {
  type GetResponseFunc = PartialFunction[String, Try[Option[String]]]
  type PutResponseFunc = PartialFunction[(String, Option[String]), Try[String]]
  type DeleteResponseFunc = PartialFunction[String, Try[String]]
  
}

trait MockHttpSender {
  
  private def notImplemented[T]:PartialFunction[Any, Try[T]] = {case _ => Failure(new NotImplementedError("The function is not supported"))}

  def sessionCreatedResponse(sessionID:String = "12345") = Success(
    s"""
      |{
      | "ID": "$sessionID"
      |} 
    """.stripMargin)
  def mockGet(pf: GetResponseFunc): HttpSender = new MockHttpSenderImpl(notImplemented, pf, notImplemented)
  def mockPut(pf: PutResponseFunc): HttpSender = new MockHttpSenderImpl(pf, notImplemented, notImplemented)
  def mockDelete(pf: DeleteResponseFunc): HttpSender = new MockHttpSenderImpl(notImplemented, notImplemented, pf)
}

/**
  * @author Peter Nerg
  */
class MockHttpSenderImpl(putResponse: PutResponseFunc, getResponse: GetResponseFunc, deleteResponse: DeleteResponseFunc) extends HttpSender {
  private def noMatch[T](path:String) = (_:Any) => Failure[T](new MatchError(s"Request data for the URI [$path] did not match provided function"))
  def put(path:String, body:Option[String]):Try[String] = putResponse.applyOrElse((path, body), noMatch(path))
  def get(path:String):Try[Option[String]] = getResponse.applyOrElse(path, noMatch(path))
  def delete(path:String):Try[String] = deleteResponse.applyOrElse(path, noMatch(path))
}

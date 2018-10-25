package org.dmonix.consul

import java.io.IOException
import java.net.{HttpURLConnection, URL}

import scala.io.Source
import scala.util.{Failure, Success, Try}

private[consul] trait HttpSender {
  def post(path:String, body:Option[String] = None)
  def put(path:String, body:Option[String] = None):Try[String]
  def get(path:String):Try[Option[String]]
}

private[consul] class ConsulHttpSender(consulHost: ConsulHost) extends HttpSender {
  private val consulURL = s"http://${consulHost.host}:${consulHost.port}"
  def post(path:String, body:Option[String] = None) = ???
  def put(path:String, body:Option[String] = None):Try[String] =
    Try {
      val req = withPath(path).openConnection().asInstanceOf[HttpURLConnection]
      req.setRequestMethod("PUT")
      req.addRequestProperty("Accept", "application/json")
      req.addRequestProperty("Content-Type", "application/json")
      body.foreach { bd =>
        req.setDoOutput(true)
        req.getOutputStream.write(bd.getBytes("UTF-8"))
      }
      req
    }.flatMap { req =>
      req.getResponseCode match {
        case it if 200 until 203 contains it => Success(req.getInputStream)
        case code => Failure(new IOException(s"Got unexpected response [$code][${req.getResponseMessage}]"))
      }
    }.map(Source.fromInputStream(_).mkString)

  def get(path:String):Try[Option[String]] =
    Try {
      val req = withPath(path).openConnection().asInstanceOf[HttpURLConnection]
      req.setRequestMethod("GET")
      req.addRequestProperty("Accept", "application/json")
      req
    }.flatMap { req =>
      req.getResponseCode match {
        case it if 200 until 203 contains it => Success(Option(req.getInputStream))
        case 404 => Success(None)
        case code => Failure(new IOException(s"Got unexpected response [$code][${req.getResponseMessage}]"))
      }
    }.map(_.map(Source.fromInputStream(_).mkString))
  
  private def withPath(path:String):URL = new URL(consulURL + "/v1"+path)
}
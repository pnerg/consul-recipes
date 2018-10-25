package org.dmonix.consul

import java.io.IOException
import java.net.{HttpURLConnection, URL}

import scala.io.Source
import scala.util.{Failure, Success, Try}

private[consul] trait HttpSender {
  def put(path:String, body:Option[String] = None):Try[String]
  def get(path:String):Try[Option[String]]
}

private[consul] class ConsulHttpSender(consulHost: ConsulHost) extends HttpSender {
  implicit class RichHttpURLConnection(con:HttpURLConnection) {
    def responseAsString:String = Source.fromInputStream(con.getInputStream).mkString
  }
  private val consulURL = s"http://${consulHost.host}:${consulHost.port}"
  def put(path:String, body:Option[String] = None):Try[String] =
    send(path, "PUT", body)
      .flatMap { req =>
      req.getResponseCode match {
        case it if 200 until 203 contains it => Success(req.responseAsString)
        case code => Failure(new IOException(s"Got unexpected response [$code][${req.getResponseMessage}]"))
      }
    }

  def get(path:String):Try[Option[String]] =
    send(path, "GET", None)
      .flatMap { req =>
      req.getResponseCode match {
        case it if 200 until 203 contains it => Success(Option(req.responseAsString))
        case 404 => Success(None)
        case code => Failure(new IOException(s"Got unexpected response [$code][${req.getResponseMessage}]"))
      }
    }
  
  private def send(path:String, method:String, body:Option[String]):Try[HttpURLConnection] =
    Try {
      val req = withPath(path).openConnection().asInstanceOf[HttpURLConnection]
      req.setRequestMethod(method)
      req.addRequestProperty("Accept", "application/json")
      req.addRequestProperty("Content-Type", "application/json")
      body.foreach { bd =>
        req.setDoOutput(true)
        req.getOutputStream.write(bd.getBytes("UTF-8"))
      }
      req
    }
  
  private def withPath(path:String):URL = new URL(consulURL + "/v1"+path)
}
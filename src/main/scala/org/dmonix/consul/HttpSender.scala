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

import java.io.IOException
import java.net.{HttpURLConnection, URL}

import scala.io.Source
import scala.util.{Failure, Success, Try}

private[consul] trait HttpSender {
  def delete(path:String):Try[String]
  def put(path:String, body:Option[String] = None):Try[String]
  def get(path:String):Try[Option[String]]
}

private[consul] class ConsulHttpSender(consulHost: ConsulHost) extends HttpSender {
  implicit class RichHttpURLConnection(con:HttpURLConnection) {
    def responseAsString:String = Source.fromInputStream(con.getInputStream).mkString
  }
  private val consulURL = s"http://${consulHost.host}:${consulHost.port}"
  def delete(path:String):Try[String] =
    send(path, "DELETE", None)
      .flatMap { req =>
        req.getResponseCode match {
          case it if 200 until 203 contains it => Success(req.responseAsString)
          case code => Failure(new IOException(s"Got unexpected response [$code][${req.getResponseMessage}]"))
        }
      }

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
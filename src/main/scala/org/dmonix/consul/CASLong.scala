/**
  *  Copyright 2019 Peter Nerg
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

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import ConsulJsonProtocol._

/**
  * Companion object to CASLong
  */
object CASLong {
  def initiate(consulHost: ConsulHost, counterPath:String, initialValue:Long): Try[CASLong] = {
      Consul(consulHost)
        .storeKeyValue(SetKeyValue(counterPath).withValue(initialValue.toString).withCompareAndSet(0))
        .map(_ => new CASLong(consulHost, counterPath))
  }
}

/**
  * Implements a compare and set Long value.
  * @author Peter Nerg
  */
class CASLong(consulHost: ConsulHost, counterPath:String) {

  private val consul = Consul(consulHost)

  /**
    * Tries to increment the counter by one and return the new value.
    * @return
    */
  def incrementAndGet(): Try[Long] = incrementAndGet(1).map(_._1)

  /**
    * Tries to increment the value by 'n' and return both stored+1 and stored+n
    * @param increment The value to increment with.
    * @return
    */
  def incrementAndGet(increment:Long): Try[(Long,Long)] = {
    @tailrec
    def recursive():Try[(Long, Long)] = readCounter
      .flatMap { res =>
        val (keyValue, currentValue) = res
        val newKeyValue = SetKeyValue(counterPath).withCompareAndSet(keyValue.modifyIndex).withValue((currentValue + increment).toString)
        consul.storeKeyValue(newKeyValue).map((_, currentValue))
      } match {
      case Failure(ex) => Failure(ex)
      case Success((true, currentValue)) => Success((currentValue+1,currentValue+increment))
      case Success((false, _)) => recursive()
    }

    recursive()
  }

  /**
    * Attempts to read the current value of the long.
    * This will fail if there is no such key or the key doesn't have a numerical value
    * @return
    */
  def currentValue(): Try[Long] = readCounter.map(_._2)

  /**
    * Attempts to read the current value of the counter.
    * This will fail if there is no such key or the key doesn't have a numerical value
    * @return
    */
  private def readCounter: Try[(KeyValue, Long)] = 
    consul
      .readKeyValue(counterPath)
      .flatMap{
        case Some(keyValue) => {
          keyValue.convertValueTo[Long] match {
            case Some(counterValue) => Success((keyValue, counterValue))
            //in case there is no value on the key  
            case None => Failure(new NumberFormatException(s"The key [$counterPath] has no value and cannot be converted to a number")) //
          }
        }
        case None => Failure(NoSuchKeyError(counterPath)) //should really not happen as we create the key, unless deleted manually
      }

}

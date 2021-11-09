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

/**
  * Companion object to CASLong
  */
object CASLong {

  /**
    * Initiates a long in Consul.
    * If the key/path does not exists in Consul the provided initial value is set.
    * @param consulHost The consul host
    * @param path The path in Consul to store the long
    * @param initialValue Initial value to set in case the path does not already exist (default 0)
    * @return
    */
  def initiate(consulHost: ConsulHost, path: String, initialValue: Long = 0): Try[CASLong] = {
    Consul(consulHost)
      .storeKeyValue(SetKeyValue(path).withValue(initialValue.toString).withCompareAndSet(0))
      .map(_ => new CASLong(consulHost, path))
  }
}

/**
  * Implements atomic distributed Long using optimistic locking mechanism.
  * The principle of changing the persisted counter/long value is to:  
  * 1) read the value from Consul including the latest ModificationIndex.  
  * 2) store the updated value to Consul using compare-and-set with the read ModificationIndex.  
  * Should the write fail due to concurrency issues, we loop back to step 1 and try again.  
  * Generally all operations will fail if there is no such key/path or the key doesn't have a numerical value.
  * @author Peter Nerg
  */
class CASLong(consulHost: ConsulHost, counterPath: String) {

  private val consul = Consul(consulHost)

  /**
    * Tries to decrement the counter by one and return the new value.
    * @return
    */
  def decrementAndGet(): Try[Long] = compareAndSet(-1)

  /**
    * Tries to decrement the value by 'n' and return the new value.
    * @param decrement The value to decrement with.
    * @return
    */
  def decrementAndGet(decrement: Long): Try[Long] = compareAndSet(-decrement)

  /**
    * Tries to increment the counter by one and return the new value.
    * @return
    */
  def incrementAndGet(): Try[Long] = compareAndSet(1)

  /**
    * Tries to increment the value by 'n' and return the new value.
    * @param increment The value to increment with.
    * @return
    */
  def incrementAndGet(increment: Long): Try[Long] = compareAndSet(increment)

  /**
    * Attempts to read the current value of the long.
    * @return
    */
  def currentValue(): Try[Long] = get.map(_._2)

  /**
    * Tries to increment the value by 'n' and return both stored+1 and stored+n
    * @param delta The delta value, could be negative for decrements
    * @return
    */
  private def compareAndSet(delta: Long): Try[Long] = {
    @tailrec
    def recursive(): Try[Long] = get
      .flatMap { res =>
        val (keyValue, currentValue) = res
        val newValue = currentValue + delta
        val newKeyValue = SetKeyValue(counterPath).withCompareAndSet(keyValue.modifyIndex).withValue(newValue.toString)
        consul.storeKeyValue(newKeyValue).map((_, newValue))
      } match {
      // terminal error, could be connection failure or missing key in Consul
      case Failure(ex) => Failure(ex)
      // key was successfully updated
      case Success((true, newValue)) => Success(newValue)
      // could not update key, most likely reason is concurrent changes. Iterate over and try again
      case Success((false, _)) => recursive()
    }

    recursive()
  }

  /**
    * Attempts to read the current value of the counter.
    * This will fail if there is no such key or the key doesn't have a numerical value
    * @return
    */
  private def get: Try[(KeyValue, Long)] =
    consul
      .readKeyValue(counterPath)
      .flatMap {
        case Some(keyValue) => {
          keyValue.value match {
            // the key has some kind of value, might still be garbage
            case Some(counterValue) =>
              Try(counterValue.toLong) match {
                // successfully parsed the value to a long
                case Success(long) => Success((keyValue, long))
                // failed to parse the value to a long, might be empty or garbage
                case Failure(_) =>
                  Failure(
                    new NumberFormatException(
                      s"The key [$counterPath] has a non-numerical value [$counterValue] and cannot be converted to a number"
                    )
                  )
              }
            // in case there is no value on the key
            case None =>
              Failure(
                new NumberFormatException(s"The key [$counterPath] has no value and cannot be converted to a number")
              )
          }
        }
        // should really not happen as we create the key, unless deleted manually
        case None => Failure(NoSuchKeyException(counterPath))
      }
}

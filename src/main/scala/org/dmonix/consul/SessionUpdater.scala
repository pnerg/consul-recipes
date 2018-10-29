package org.dmonix.consul

import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import scala.concurrent.duration.FiniteDuration

import scala.collection._

/**
  * Helps to keep created sessions alive by periodic renewals.
  * @author Peter Nerg
  */
private[consul] trait SessionUpdater { 
  consul : Consul => 
  
  private val scheduler = new ScheduledThreadPoolExecutor(5)
  
  private val sessions = mutable.Map[SessionID, ScheduledFuture[_]]()

  /**
    * Registers a session to be kept alive.
    * @param sessionID The id of the session
    * @param ttl The time-to live for the session
    */
  def registerSession(sessionID:SessionID, ttl:FiniteDuration):Unit = {
     val runnable = new Runnable {
       override def run(): Unit = consul.renewSession(sessionID)
     }
    val sf = scheduler.scheduleWithFixedDelay(runnable, 0, Math.round(ttl.toMillis*0.80), TimeUnit.MILLISECONDS)
    sessions.put(sessionID, sf)
  }

  /**
    * Unregisters a session from the keep alive procedure
    * @param sessionID
    */
  def unregisterSession(sessionID:SessionID):Unit = {
    sessions.remove(sessionID).foreach(_.cancel(true))
  }

  /**
    * Returns a sequence of all sessions registered for automatic renewal.
    * @return
    */
  def registeredSessions:Seq[SessionID] = sessions.keys.toSeq
  
}

/*
 * #%L
 * WSB Core
 * %%
 * Copyright (C) 2008 - 2017 OpenDesignFlow.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.odfi.ubroker.core.network

import org.odfi.tea.listeners.ListeningSupport
import org.odfi.tea.errors.ErrorSupport
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 *
 * The NetworkContext Class is used by messages to refer back to the network layer they were send/from to
 *
 */
class NetworkContext extends ListeningSupport with ErrorSupport {

  var enableInputPayloadSignaling = false
  var inputPayloadsSemaphore = new Semaphore(0, true)

  this.onClose {
     if (enableInputPayloadSignaling) {
       try {
        inputPayloadsSemaphore.release(inputPayloadsSemaphore.getQueueLength)
       } catch {
         case e : Throwable => 
           inputPayloadsSemaphore.drainPermits()
       }
      }
  }
  
  def onClose(cl: => Unit) = {
    on("close") {
       cl
    }
  }

  // USe semaphore for sync

  def triggerInputPayloadSemaphore = {
    if (enableInputPayloadSignaling) {
      //if (inputPayloadsSemaphore.hasQueuedThreads()) {
      //println("Currentely waiting: " + inputPayloadsSemaphore.getQueueLength + "-> " + inputPayloadsSemaphore.availablePermits())

      inputPayloadsSemaphore.release(1);
      //inputPayloadsSemaphore.release(inputPayloadsSemaphore.getQueueLength)
      //}
    }
  }

  def waitForInputPayload(timeMS: Long) = {
    if (enableInputPayloadSignaling) {
      try {
       // println("Releases before waiting: "+inputPayloadsSemaphore.availablePermits())
       
        inputPayloadsSemaphore.tryAcquire(timeMS, TimeUnit.MILLISECONDS)
       // println("Done")
      } catch {
        case e: Throwable =>
          e.printStackTrace()
      }
    }
  }

  /**
   * Attachments map to store any kind of extra objects in the network context
   */
  var attachments = Map[String, Any]()

  def apply(t: (String, Any)) = {
    this.attachments = this.attachments + t
  }

  def apply[T <: Any](t: String): Option[T] = {
    this.attachments.get(t) match {
      case Some(v) => Some(v.asInstanceOf[T])
      case None => None
    }
  }

  /**
   * The qualifier should uniquely identify the Context, so that connectors can initiate connections or send responses to the right client
   */
  var qualifier: String = null

  override def toString: String = qualifier match {
    case null => qualifier.toString
    case _ => qualifier
  }

}

object NetworkContext {

  /**
   * Returns a new instance of network context with provided qualifier
   */
  def apply(contextQualifier: String): NetworkContext = {
    var nc = new NetworkContext
    nc.qualifier = contextQualifier
    nc
  }

  /*obbject NetworkString(var protocolStack : String, var messageType : String, var connectionString: String) {
    
    
    
  }*/

  object NetworkString {

    /**
     *
     * From context string:
     *
     * - take part before "://"
     * - from "+" separated stack, the last one is the message type, so ignore it
     *
     */
    def unapply(str: String): Option[(String, String, String)] = {

      """(?:(.*)\+)?(.+)://(.*)""".r.findFirstMatchIn(str) match {
        case Some(m) => Some(m.group(1), m.group(2), m.group(3))
        case None => None
      }

    }

  }

}

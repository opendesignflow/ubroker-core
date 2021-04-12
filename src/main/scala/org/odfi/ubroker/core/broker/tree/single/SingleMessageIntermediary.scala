/*-
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
package org.odfi.ubroker.core.broker.tree.single

import org.odfi.ubroker.core.message.Message
import java.util.concurrent.locks.ReentrantLock
import scala.reflect.ClassTag
import org.odfi.ubroker.core.broker.tree.MessageIntermediary

class SingleMessageIntermediary[MT <: SingleMessage](implicit val ttag: ClassTag[MT]) extends MessageIntermediary[MT] {

  //println(s"Creating Single Message intermediary from: " + messageType)

  def messageType = ttag.runtimeClass.asInstanceOf[Class[MT]]

  /**
   * Busy Locks are store in a map pro virtual Channel
   */
  var busyLocks = Map[String, ReentrantLock]()

  //var busyLock = new ReentrantLock(false)

  /**
   * Override catcher to throttle receives
   * Last in Train messages are waiting extra
   */
  override def onDownMessage(cl: MT => Unit) = {
    super.onDownMessage {
      message =>

        // Get Lock
        //-----------------
        var busyLock = busyLocks.synchronized {
          busyLocks.get(message.getVirtualChannelAsString) match {
            case Some(lock) => lock
            case None =>
              var lock = new ReentrantLock(false)
              this.busyLocks = this.busyLocks + (message.getVirtualChannelAsString -> lock)
              lock
          }
        }

        // println(s"Checking Lock for message: "+message+" -> "+busyLock.tryLock()+" on "+this)
        // busyLock.tryLock()
        busyLock.tryLock() match {
          case true =>
            //println(s"Got Lock for message: "+message+" -> on "+this)
            try {
              cl(message)
            } finally {
              busyLock.unlock()
            }
          case false if (message.lastInTrain) =>
            busyLock.lock()
            try {
              cl(message)
            } finally {
              busyLock.unlock()
            }
          case false =>
        }
    }

  }

}

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
package org.odfi.ubroker.core.broker.tree

import org.odfi.ubroker.core.message.Message
import scala.reflect.ClassTag
import scala.reflect._

/**
 * A Specialized intermediary that focuses on accepting a specific message type
 *
 * The User can just provide some more convenient function to handle messages, and sub implementations
 * can provide default behavior in case of errors for example
 *
 *
 */
trait MessageIntermediary[MT <: Message] extends Intermediary {

  //val ttag: ClassTag[MT]

  // Message closure
  var messageDownClosures = List[MT => Unit]()
  var messageUpClosures = List[Message => Unit]()

  // Error closure
  var errorClosures = List[((MT, Throwable) => Unit)]()

  // User Interface
  //-----------------

  def messageType: Class[MT]

  def onDownMessage(cl: MT => Unit) = this.messageDownClosures = messageDownClosures :+ cl
  def onUpMessage[T <: Message](cl: T => Unit) = this.messageUpClosures = messageUpClosures :+ cl.asInstanceOf[Message => Unit]

  def onError(cl: (MT, Throwable) => Unit) = this.errorClosures = this.errorClosures :+ cl

  // Implementation
  //-----------------------

  //-- Only accept messages of provided type
  this.acceptDown { (m : Message) =>
    //println(s"MI accepting? -> "+m.getClass.getSuperclass+"->"+messageType.isAssignableFrom(m.getClass()))
    messageType.isAssignableFrom(m.getClass())
  }
  // this.acceptUp{ m =>  messageType.isAssignableFrom(m.getClass())  }

  //-- Call on message closure
  this.downClosure = {

    m =>

      try {
        messageDownClosures.foreach(_(m.asInstanceOf[MT]))
      } catch {
        case e: ResponseException => throw e
        case e: Throwable => 
          this.errorClosures.foreach(_(m.asInstanceOf[MT], e))
         
      }

  }

  //-- Call on message closure
  this.upClosure = {

    m =>

      try {

        messageUpClosures.foreach(_(m.asInstanceOf[MT]))
      } catch {
        case e: ResponseException => throw e
        case e: Throwable => this.errorClosures.foreach(_(m.asInstanceOf[MT], e))
      }

  }

}

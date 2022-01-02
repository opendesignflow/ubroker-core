
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
package org.odfi.ubroker.core.message

import java.nio.ByteBuffer
import org.odfi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.ooxoo.core.buffers.structural.xelement_base
import org.odfi.ubroker.core.message.soap.SOAPMessage
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.ubroker.core.message.soap.JSONSOAPMessage
import org.odfi.tea.errors.ErrorSupport
import org.odfi.ubroker.core.broker.tree.single.SingleMessage

/**
 * @author rleys
 *
 */
trait Message extends ErrorSupport {

  /**
   * Qualifier used in tree processing to filter out subtrees
   */
  var qualifier = ""

  var networkContext: Option[NetworkContext] = None

  /**
   * If for example, this message is a response to another one, this is the request message
   */
  var relatedMessage: Option[Message] = None

  /**
   * Set to true if this message or a response message has been upped (answered so to say)
   */
  var upped = false

  /**
   * To be implemented by messages for connectors to retrieve bytes for this message
   */
  def toBytes: ByteBuffer
  
  /**
   * Set to true to mark the message as being the last in a series of related messages
   * This is useful for some intermediaries to ignore messages, but force processing a lastInTrain message
   */
  var lastInTrain =  false

  // Error Handling
  //-----------------

  /**
   * if an intermediary fails by throwing an exception, this doesn't mean the whole tree processing fails, because handling might differ based on applications
   * However, errors are stored here so that some special intermediary can log errors or do some other stuff
   */
  //var errors = List[Throwable]()

  /**
   * Execute closure on all errors, and remove errors from list
   * @return the Gathered Result list
   */
  def consumeAndMapErrors(cl: Throwable => Any): List[Any] = {

    var res = List[Any]()
    this.errors = this.errors.filter {
      case e: Throwable =>
        res = cl(e) :: res;

        false
    }
    
    //println(s"-------> after consume errors: "+this.errors.size)

    res

  }
  
  def clearErrors =this.resetErrors

  // Transformation: 
  //------------------------

  /**
   * Same as transform with explicit class, but transforms to current type (copies current message type)
   * @see transform(Class[T](cl)
   */
  def transform(cl: this.type => Any): this.type = {
    transform[this.type](this.getClass().asInstanceOf[Class[this.type]])(cl)
  }

  /**
   * Creates a new Message of the provided class, with required stuff already copied, like network context etc...
   * Then applies the provided closure to the new message
   */
  def transform[T <: Message](mc: Class[T])(cl: T => Any): T = {

    // Create
    //-----------
    var m = mc.getDeclaredConstructor().newInstance()

    //-- Copy required parameters
    m.networkContext = this.networkContext
    m.errors = this.errors
    m.relatedMessage = this.relatedMessage
    m.qualifier = this.qualifier

    // Apply
    //----------
    cl(m)

    // Return
    //--------------
    m
  }

  // Apply functions
  //---------

  /**
   * Record an error into this message
   */
  def apply(e: Throwable) = {

    this.addError(e)

  }
  
  // Ignoring
  //----------------
  
  /**
   * Set to true to ignore processing by current intermediary and keep going
   * MUST be reset by basic intermediary implementation
   */
  var ignoreCurrentIntermediary = false

}

trait MessageFactory {

  def apply(data: Any): Message

}

object Message {

  object Qualifier {

    /**
     * Return a qualifier in the form of {ns}:ElementName from provided ElementBuffer
     */
    def apply[T <: ElementBuffer](elt: T): String = apply(elt.getClass())

    def apply[T <: ElementBuffer](elt: Class[T]): String = {

      xelement_base(elt) match {
        case null => ""

        case elt if (elt.ns != null && elt.ns.length() > 0) => s"{${elt.ns}}:${elt.name}"

        case elt => s"${elt.name}"

      }

    }
  }

  // Factories
  //----------------------
  var messageFactories = Map[String, MessageFactory]()

  //-- Message types known to this library
  this("soap", SOAPMessage)
  this("json-soap", JSONSOAPMessage)

  /**
   * Returns a Message Instance matching the given message type
   */
  def apply(messageType: String): Option[MessageFactory] = messageFactories.get(messageType)

  def apply(messageType: String, factory: MessageFactory) = {

    messageFactories = messageFactories + (messageType -> factory)

  }

}

/**
 *
 */
trait UpMessage {

}

trait InternalMessage extends Message with SingleMessage {
  def toBytes = throw new RuntimeException("Internal Message not intended for output, no bytes to be produced")
}


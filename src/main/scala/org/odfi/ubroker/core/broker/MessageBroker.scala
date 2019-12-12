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
package org.odfi.ubroker.core.broker

import org.odfi.ubroker.core._
import org.odfi.ubroker.core.broker.tree._
import org.odfi.ubroker.core.message.Message
import org.odfi.ubroker.core.message.UpMessage
import org.odfi.tea.logging.TLogSource

class TopBrokerIntermediary(val engine : WSBEngine) extends Intermediary {
    
  
    
    /**
     * Send message through network
     */
    upClosure = {
      message =>
        logFine[MessageBroker]("Message reached top, dispatch to network")
        engine.network.send(message)

    }

  }

class MessageBroker(var engine: WSBEngine) extends Lifecycle with TLogSource {

  

  // Brokering Tree
  //---------------------

  /**
   * The default brokering tree used. can be easily improved by API
   */
  val brokeringTree = new TopBrokerIntermediary (engine)
  

  val messageReceiveClosure = {

    msg: Message =>

      msg match {
        case m: UpMessage =>

          logFine[MessageBroker]("received Message to up")

          brokeringTree.up(msg)

        case _ =>

          logFine[MessageBroker]("Broker Got Down Message: " + msg.getClass)
          logFine[MessageBroker]("-> Qualifier " + msg.qualifier)
          brokeringTree.down(msg)
      }

    // broker

  }

  /**
   * Add an intermediary to the root  Intermediary
   */
  def <=(intermediary: Intermediary) = {
    brokeringTree.<=(intermediary.asInstanceOf[Intermediary])
    intermediary
  }

  def prepend(intermediary: Intermediary) = brokeringTree.prepend(intermediary.asInstanceOf[Intermediary])

  def -=(intermediary: Intermediary) = brokeringTree.-=(intermediary.asInstanceOf[Intermediary])

  // Engine Connection
  //-------------

  //-- Listen for Messages to be handled

  /**
   * Method to send an event to the local Engine internal Bus
   */
  //def ! ( msg :  AnyRef) = this.engine ! msg

  // Lifecycle
  //------------------------

  /**
   * Init Message Broker
   *
   */
  def lInit = {

    // Register Message Closure to aib
    //engine.localBus.registerClosure(messageReceiveClosure)

  }

  /**
   * Start Message Broker
   */
  def lStart = {

    // Nothing to do

  }

  def lStop = {

    // Nothing to do

  }

}
object MessageBroker {

}

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
package com.idyria.osi.wsb.lib.soap

import com.idyria.osi.wsb.lib.client.WSBClientEngine
import org.odfi.ubroker.core.broker.tree.Intermediary
import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.ubroker.core.message.soap.SOAPMessage
import org.odfi.ubroker.core.message.UpMessage
import org.odfi.ubroker.core.message.Message
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.ubroker.core.message.soap.SOAPIntermediary
import com.idyria.osi.wsb.lib.client.WSBClientEngine
import org.odfi.ubroker.core.message.soap.SOAPMessagesHandler
import org.odfi.ubroker.core.broker.tree.Intermediary
import org.odfi.ubroker.core.message.soap.SOAPMessage
import com.idyria.osi.wsb.lib.client.WSBClientEngine
import org.odfi.ubroker.core.message.soap.Fault
import org.odfi.ubroker.core.WSBEngine
import org.odfi.ubroker.core.network.connectors.AbstractConnector
import scala.reflect.ClassTag

/**
 *
 * A Client Engine with default WSA intermediary as first broker child
 *
 */
class WSAClientEngine(e: WSBEngine = new WSBEngine) extends WSBClientEngine(e) {

  // Init/Start/Start
  //-----------------
  def start = {
    engine.lInit
    engine.lStart
  }
  
  def stop = {
    engine.lStop
  }

  //-- Connector
  //---------------------
  def apply(connector:AbstractConnector[_ <: NetworkContext]) = {
    this.engine.network.addConnector(connector)
  }
  
  //-- Add WSA
  //------------------
  var wsaIntermediary = new WSAIntermediary
 /* this.engine.broker <= wsaIntermediary
  this.engine.broker.brokeringTree.upStart = wsaIntermediary

  //-- Intermediary add directly through engine shortcuts to wsaIntermediary
  def <=(int: Intermediary[_]) = wsaIntermediary <= int*/

  //-- Add  SOAPMessage Intermerdiary for responses
  //------------------------
  val responsesHandler = new SOAPMessagesHandler {

    //onDownMessage[Fau]

    // Errors
    //---------------
    on[Fault] {
      (message, f) => 
        //aib ! f
    }

  }
  //this <= responsesHandler

  // Send 
  //-------------------

  var currentNetworkID : Option[String] = None
  
  import scala.reflect.runtime.universe._
  import scala.reflect._
  /**
   * Send to default selected NetworkID
   */
  def send[T <: ElementBuffer, RT <: ElementBuffer : ClassTag ](payload: T)(respClosure: RT => Unit) : Unit = {
    
    send(payload,currentNetworkID.get)(respClosure)
    
  }
  
  
  
  /**
   * Send a Created SOAP Message with message as payload to the network
   *
   * The provided response closure is consumable and will be removed once the response has been received
   */
  def send[T <: ElementBuffer, RT <: ElementBuffer : ClassTag](payload: T, networkId: String)(respClosure: RT => Unit) : Unit = {

    // Get Type of response
    //-----------------
    /*var closureMethod = respClosure.getClass().getMethods().filter {
      m => m.getName() == "apply" && m.getReturnType() == Void.TYPE
    }.head
    var elementType = (closureMethod.getParameterTypes()(0).asInstanceOf[Class[RT]])*/

    var t = classTag[RT]
    var elementType = t.runtimeClass.asInstanceOf[Class[RT]]
    
    

    // Register Response closure
    //-------------------
    if (respClosure!=null) {
      responsesHandler.onMessageType(elementType, {
      (message, m: RT) =>

        try {
          respClosure(m)
        } finally {

          // Consume closure
          responsesHandler.removeHandler(respClosure)

        }

    })
    }
    

    // Create Message
    //----------------
    var message = new SOAPMessage with UpMessage

    message.body.content += payload

    message.qualifier = Message.Qualifier(payload)

    // Set network context to string
    //--------------
    message.networkContext = Some(NetworkContext(networkId))

    // Send
    //----------
    engine.network.send(message)


  }

  def sendAndForget[T <: ElementBuffer, RT <: ElementBuffer : ClassTag](payload: T, networkId: String)  : Unit = (send(payload,networkId)_)(null)
  
}

object WSAClientEngine {

  var intern = new WSAClientEngine

  def apply(): WSAClientEngine = this.intern

}

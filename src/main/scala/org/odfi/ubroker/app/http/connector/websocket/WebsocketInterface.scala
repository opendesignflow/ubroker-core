/*-
 * #%L
 * WSB Webapp
 * %%
 * Copyright (C) 2013 - 2017 OpenDesignFlow.org
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
package org.odfi.ubroker.app.http.connector.websocket

import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

import org.odfi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.ooxoo.lib.json.JsonIO
import org.odfi.tea.logging.TLogSource
import org.odfi.ubroker.core.message.soap.Envelope
import org.odfi.ubroker.core.message.soap.EnvelopeBody
import org.odfi.ubroker.core.message.soap.JSONSOAPMessage
import org.odfi.ubroker.core.network.connectors.tcp.TCPNetworkContext


/**
 * @author zm4632
 */
class WebsocketInterface(val nc: TCPNetworkContext) extends TLogSource {

  def writeMessage(el: ElementBuffer) = {

    // Produce XML Bytes
    //-------------
    //var res = StAXIOBuffer(el, true)
    //println(s"Converting to JSON WS message");
   
    var res = JsonIO(el, true)
    //
   // logInfo(s"Converting to JSON WS message: " + res)
    
    
    
     var bytes = ByteBuffer.wrap(res.getBytes)
    // nc.synchronized {
      // Send
      //---------------
      //println(s"Sending WS message");
      nc.relatedConnector.send(bytes, nc)
    //}
  }

  def writeSOAPPayload(el: ElementBuffer) = {

    //println(s"1 Converting to JSON WS message");
    var soap = new JSONSOAPMessage
    soap.body = EnvelopeBody()
    soap.body.content += el

    // println(s"2 Converting to JSON WS message");
    writeMessage(soap)

  }

  def catchNextDone = {

    /* receivedSem = new Semaphore(0)
    nc.relatedConnector.onWithTransient[Envelope]("message.received") {
      soap =>
       // println(s"Done Received SOAP")
        soap.body.content.find {
          case done: Done =>
            
            true
          case other => false
        } match {
          case Some(done) =>
           // println(s"Done Received DONE")
              receivedSem.release
          case None => 
        }
      
    }
    var closeId = nc.relatedConnector.on("close") {
      receivedSem.acquire
    }
    receivedSem.acquire
    nc.relatedConnector.deregister(closeId)
    
    */

  }

}

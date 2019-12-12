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

import org.odfi.ubroker.app.http.message.HTTPIntermediary
import org.odfi.ubroker.app.http.message.HTTPResponse
import java.util.Base64
import org.odfi.ubroker.app.http.message.HTTPMessage
import org.odfi.ubroker.app.http.message.HTTPRequest
import org.odfi.ubroker.app.http.message.HTTPPathIntermediary
import org.odfi.ubroker.app.http.session.Session
import org.odfi.ubroker.core.network.connectors.tcp.TCPNetworkContext

trait WebsocketIntermediary extends HTTPIntermediary {

  
  // Interface Storage
  //-------------
  var websocketPool = scala.collection.mutable.Map[Session, WebsocketInterface]()

  def getInterface(req:HTTPRequest) = req.hasSession match {
    case true =>
      websocketPool.synchronized {
        websocketPool.get(req.getSession.get)
      }
    case other => None
  }
  
  this.acceptDown[HTTPRequest] { 
    req =>
      //println("Testing WS Intermediary request: ")
    (req.getParameter("Connection"), req.getParameter("Upgrade"), req.getParameter("Sec-WebSocket-Version")) match {

      case (Some(conn), Some(protocol), Some("13")) if (conn.toLowerCase().contains("upgrade") && protocol.toLowerCase() == "websocket") => true
      case _ => false
    }
  }

  // Main Response
  //----------------------
  this.onDownMessage {
    req =>
      //println(s"-- Got Websocket message");

      //-- Convert Key
      var key = req.getParameter("Sec-WebSocket-Key") match {
        case Some(key) =>

          var r = new HTTPResponse
          r.code = 101
          r.addParameter("Connection", "Upgrade")
          r.addParameter("Upgrade", "websocket")
          r.addParameter("Sec-WebSocket-Protocol", "soap")

          //-- Answer with upgrade

          r.addParameter("Sec-WebSocket-Accept", Base64.getEncoder.encodeToString(WebsocketIntermediary.md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes)))

          response(r, req)

        case None =>
          var r = new HTTPResponse
          r.code = 400
          response(r, req)
      }

  }

  // Response OK
  //-----------------
  this.onDownMessage {
    req =>

      // TLog.setLevel(classOf[WebsocketProtocolhandler], TLog.Level.FULL)

      if (req.upped) {

       // println(s"-- Saving WS Connection to pool: "+this.hashCode()+", session is: "+req.getSession.get);
        
        this.websocketPool.synchronized {
          
          logFine[WebsocketIntermediary](s"Websocket opened for: " + req.getSession)
          var interface = new WebsocketInterface(req.networkContext.get.asInstanceOf[TCPNetworkContext])
          websocketPool.update(req.getSession.get, interface)
          
          req.networkContext.get.enableInputPayloadSignaling = true  
          req.networkContext.get.onClose {
 
            websocketPool.synchronized {
               websocketPool -= req.getSession.get
            }
            //websocketPool -= req.getSession.get
            logFine[WebsocketIntermediary](s"Closing Websocket with state: ${req.networkContext}, remaning: " + websocketPool.size)
          }

        }
        
        // Finished connection
        this.@->("websocket.connected")
        
        //println(s"-- Done Saving WS Connection to pool: "+this.hashCode());

        //-- Send ack 
        //logFine[WebsocketIntermediary](s"Sending HearthBeat acknowledge")
        //Thread.sleep(500) // Wait a bit to let webpage finish loading js
        //println(s"Say Hello");

        //var hb = new HeartBeat
        //hb.time = System.currentTimeMillis()
        //interface.writeSOAPPayload(hb)

        /*var soap = new SOAPMessage
          soap.body.content += new Ack*/

      }
  }

  // Events
  def onWebsocketConnected(cl: => Any) {
    this.on("websocket.connected") {
      cl
    }
  }
  
}

class WebsocketPathIntermediary(p: String) extends HTTPPathIntermediary(p) with WebsocketIntermediary

object WebsocketIntermediary {

  val md = java.security.MessageDigest.getInstance("SHA-1");

}

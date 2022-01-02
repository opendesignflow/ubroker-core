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
package org.odfi.ubroker.lib.discovery

import java.net.InetAddress
import org.odfi.ubroker.core.network.NetworkContext

import java.net.DatagramSocket
import java.net.DatagramPacket
import org.odfi.ooxoo.core.buffers.structural.xelement
import org.odfi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.ooxoo.core.buffers.datatypes.XSDStringBuffer
import org.odfi.ooxoo.core.buffers.structural.AnyXList
import org.odfi.ubroker.core.message.soap.SOAPMessage
import org.odfi.ooxoo.core.buffers.structural.xattribute
import org.odfi.ubroker.core.message.Message

import java.nio.ByteBuffer
import org.odfi.ooxoo.core.buffers.datatypes.IntegerBuffer
import org.odfi.ooxoo.core.buffers.datatypes.XSDStringBuffer.convertStringToXSDStringBuffer
import org.odfi.ooxoo.core.buffers.datatypes.LongBuffer
import org.odfi.ubroker.core.network.connectors.AbstractConnector
import org.odfi.ooxoo.core.buffers.structural.XList
import org.odfi.tea.logging.TLogSource
import org.odfi.tea.listeners.ListeningSupport

import java.util.Timer
import java.util.TimerTask
import org.odfi.ubroker.core.network.connectors.tcp.TCPConnector
import org.odfi.tea.thread.ThreadLanguage
import org.odfi.ubroker.lib.soap.Action

/**
 * This class only sends a UDP Broadcast discovery SOAP Message when in server mode, and in client waits for this message
 *
 * The server tries to send an uid, which associated with the hostname should allow the client connector
 * to filter out already received discovery packets, and avoid abusive application trigger
 *
 *
 * @listeningPoint service.discovered Service
 *
 */
class DiscoveryConnector(var serviceName: String, var port: Int = 8891) extends AbstractConnector[DiscoveryNetworkContext] with TLogSource with ListeningSupport with ThreadLanguage {

  

  // Configuration
  //-----------------------

  //-- Send Interval in ms
  var sendIntervalMs = 1000

  // Client Filtering
  //--------------------------
  var discoveredMap = Map[String, (Service, Long)]()

  // BC Message
  //--------------------
  var message = new DiscoveryMessage

  // Ensure Datatypes are registered in factories
  AnyXList(classOf[Service])
  AnyXList(classOf[Discovery])
  Message("soap", SOAPMessage)

  
  //-- Thread definition
  var thread: Option[Thread] = None

  def getThread = thread.getOrElse {

    var th = createThread {

      run

    }
    th.setDaemon(false)
    thread = Some(th)
    th
  }
  
  // Run
  //------------

  def run = {

    this.direction match {

      // Client -> Try to receive UDP info
      //--------------
      case AbstractConnector.Direction.Client =>

        //-- Record already seen Discovered instances to avoid repeating

        //-- Bind to Catchall address
        // println("Client create socket: ")
        var socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"))


        on("stop") {
          socket.close()
        }

        while (!this.stopThread) {

          //  println("Client waiting for datas: ")
          // 

          //-- prepare datas holder
          var datas = new Array[Byte](4096)
          var packet = new DatagramPacket(datas, datas.length);

          //-- Receive
          try {

            socket.receive(packet);

            //-- Got Something
            var dataString = new String(packet.getData()).trim
            //println(s"Client got length: ${packet.getLength()} '"+dataString+"'")

            //-- Parse
            // var soapMessage = Message.apply("soap").get.apply(ByteBuffer.wrap(datas,0,packet.getLength()-1))
            var soapMessage = Message.apply("soap").get.apply(ByteBuffer.wrap(dataString.getBytes())).asInstanceOf[SOAPMessage]

            //-- Get latest next service discovery updated based on Timeout and timestamp
            var discovery = soapMessage.body.content(0).asInstanceOf[Discovery]
            var nextUpdate = discovery.interval.data + System.currentTimeMillis()

            //-- Try to filter out from hostname+uid and Record in map if necessary
            //---------------------
            soapMessage.header.content.collectFirst { case h: Service => h } match {

              // Found Service
              //--------------
              case Some(service) =>

                // Packet identifier
                var packetIdentifier = s"${service.uid}@${service.hostname}"

                // Search in map
                var serviceRecord = discoveredMap.getOrElse(packetIdentifier, {

                  // Not found,send and return for recording 
                  this.network.dispatch(soapMessage)
                 

                  //-- Save
                  discoveredMap = discoveredMap + (packetIdentifier -> (service, nextUpdate))
                  this.@->("service.discovered", service)
                  (service, nextUpdate)
                })

                // Update Map Record next update
                discoveredMap = discoveredMap.updated(packetIdentifier, (serviceRecord._1, nextUpdate))

              //serviceRecord = discoveredMap(packetIdentifier)
              // println(s"Updated next update for ${serviceRecord._1.name} to ${serviceRecord._2}")

              case _ =>

                // No Service Header found -> fail
                logWarn[DiscoveryConnector](s"Could not receive properly Discovery message as it does not contain any Service header: " + soapMessage.toXMLString)
            }
            // EOF Receive message

          } catch {

            // IN case of IO error, forget about this connection
            case e: java.io.IOException =>

            case e: Throwable           => throw e
          }

        }

      // Server -> Send stuff
      //----------------
      case AbstractConnector.Direction.Server =>

        //-- Get Address for official hostname
        var addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName())

        //-- Send for all of them
        var bcAddresses = addresses.map {
          a =>

            var addrComponents = a.getHostAddress.split("""\.""")
            addrComponents.update(3, "255")
            addrComponents.mkString(".")

        }

        //-- Create Socket for sending
        var bcSocket = new DatagramSocket()

        //-- Send to broadcast addresses
        //----------------
        while (!this.stopThread) {

          //-- Set Message payload
          message.body.content.clear()
          var discovery = new Discovery
          discovery.interval = this.sendIntervalMs.toLong
          discovery.timestamp = System.currentTimeMillis()
          message.body.content += discovery

          //-- Do Send
          bcAddresses.foreach {
            bcA =>
              //println(s"BCAddress: $bcA")

              //-- Create Message
              var bytes = message.toBytes

              //println("Sending: "+new String(bytes.array()))

              var packet = new DatagramPacket(bytes.array(), bytes.remaining, InetAddress.getByName(bcA), port)

              //-- Send
              bcSocket.send(packet)

          }

          //-- Wait
          try {
            Thread.sleep(sendIntervalMs)
          } catch {
            case e: Throwable => e.printStackTrace()
          }

        }
        // EOF Thread loop

        // Close
        bcSocket.close()

    }
    // EOF Server mode

  }
  
  //-- Check services timed stuff
  def startCheckServices = {
    
    // Create Daemon timer
    var timer = new Timer("DiscoveryServicesCheck",true)
    timer.schedule(new TimerTask() {
      def run = {
        checkServices
      }
    },0,1000)
  }

  // Client Mode
  //-----------------------

  /**
   * Cleans the services from the ones which have expired, and call on the listeners
   *
   * @listeningPoint service.expired
   */
  def checkServices  : Unit = {

    logFine[DiscoveryConnector](s"Checking services")
    this.discoveredMap.filter {
      case (identifier, (service, nextUpdate)) =>
        nextUpdate < System.currentTimeMillis()
    }.foreach {
      case (identifier, (service, nextUpdate)) =>

        logFine[DiscoveryConnector](s"Service ${service.name} next update was at $nextUpdate, but now is ${System.currentTimeMillis()}")
        @->("service.expired", service)
        this.discoveredMap = this.discoveredMap - identifier
    }

  }

  // Add Some services
  //------------------
  def addService(name: String): Service = {
    var service = new Service
    service.name = name
    service.hostname = InetAddress.getLocalHost().getHostName()
    service.uid = getClass.hashCode().toLong

    message.header.content += (service)

    // Add All Connectors in Service Definition services
    //-----------------
    /*this.network.connectors.filterNot(_ == this).foreach {

      c =>

        var connectorDesc = new Connector
        connectorDesc.protocolStack = s"${c.protocolType}+${c.messageType}"
        connectorDesc.connectionString = s"${c.protocolType}+${c.messageType}//${service.hostname}"
        
        if(c.isInstanceOf[TCPConnector]) {
          connectorDesc.connectionString = s"tcp+${connectorDesc.connectionString}:${c.asInstanceOf[TCPConnector].port}"
        }
        
        service.connectors += connectorDesc

    }*/
    
    service
  }

  // Send : No need to send anything
  //-----------------
  def send(msg: org.odfi.ubroker.core.message.Message): Boolean = throw new RuntimeException("DiscoveryConnector is not designed to send any messages")
  def send(data: java.nio.ByteBuffer, context: DiscoveryNetworkContext): Unit = throw new RuntimeException("DiscoveryConnector is not designed to send any messages")
  def send(data: java.nio.ByteBuffer): Unit = throw new RuntimeException("DiscoveryConnector is not designed to send any messages")

  def canHandle(msg: org.odfi.ubroker.core.message.Message): Boolean = false

  // Members declared in org.odfi.ubroker.core.Lifecycle
  //-------------------

  protected def lInit: Unit = {

  }

  /**
   * Create base Message with available connectors
   *
   */
  override def lStart: Unit = {

    // Set WSA Action to SOAP Message
    //--------------------
    var action = new Action
    action.data = "com.idyria.osi.wsb.lib.discovery"
    message.header.content += (action)

    // Prepare service definition
    //------------
    var service = new Service
    service.name = serviceName
    service.hostname = InetAddress.getLocalHost().getHostName()
    service.uid = getClass.hashCode().toLong

    message.header.content += (service)

    // Add All Connectors in Service Definition services
    //-----------------
    this.network.connectors.filterNot(_ == this).foreach {

      c =>

        var connectorDesc = new Connector
        connectorDesc.protocolStack = s"${c.protocolType}+${c.messageType}"
        connectorDesc.connectionString = s"${c.protocolType}+${c.messageType}//${service.hostname}"
        
        if(c.isInstanceOf[TCPConnector]) {
          connectorDesc.connectionString = s"tcp+${connectorDesc.connectionString}:${c.asInstanceOf[TCPConnector].port}"
        }
        
        message.header.content.collect{ case h : Service => h }.foreach {
          s => s.connectors += connectorDesc
        }
        

    }

    // Start Services Check
    //------------------------
    startCheckServices
    
    super.lStart
  }

  /**
   * Clean and stop
   */
  override def lStop = {
    super.lStop
    //this.interrupt()
  }

}

class DiscoveryNetworkContext extends NetworkContext {

}

/**
 * Discovery Message is a SOAP Envelope
 */
class DiscoveryMessage extends SOAPMessage {

}

/**
 * Generic Discovery informations payload in <env:Body>
 */
@xelement(name = "Discovery")
class Discovery extends ElementBuffer {

  @xattribute
  var interval: LongBuffer = null

  @xattribute
  var timestamp: LongBuffer = null

}

@xelement(name = "Service")
class Service extends ElementBuffer {

  @xattribute
  var name: XSDStringBuffer = null

  @xattribute
  var hostname: XSDStringBuffer = null


  @xattribute
  var uid: LongBuffer = null

  @xelement(name = "Connector")
  var connectors = XList { new Connector }

}

@xelement(name = "Connector")
class Connector extends ElementBuffer {

  @xattribute
  var protocolStack: XSDStringBuffer = null

  @xattribute
  var connectionString: XSDStringBuffer = null

  
  @xattribute
  var port: IntegerBuffer = null
  
}



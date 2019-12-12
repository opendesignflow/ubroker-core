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

package org.odfi.ubroker.core.network.connectors

import org.odfi.ubroker.core.Lifecycle
import org.odfi.ubroker.core.message._
import java.util.concurrent._
import java.nio._
import scala.language.implicitConversions
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.ubroker.core.network.Network
import org.odfi.ubroker.core.network.protocols.simple.SimpleMessageTCPConnector
import org.odfi.tea.logging.TLogSource
import org.odfi.tea.listeners.ListeningSupport

/**
 * Base class for Connectors.
 * Contains the base parameters for handling connectors.
 *
 * The run() method of Thread should implement the long running I/O handling
 *
 * As type parameter must be given the NetworkContext type for the connector implementation
 *
 * @author rleys
 *
 */
abstract class AbstractConnector[NT <: NetworkContext]  extends Lifecycle with TLogSource with ListeningSupport {
 
  /**
   * Network This Connector is registered under
   */
  var network: Network = null

  /**
   * CLIENT/SERVER Direction
   */
  var direction = AbstractConnector.Direction.Server

  def setClient = direction = AbstractConnector.Direction.Client
  def setServer = direction = AbstractConnector.Direction.Server
  
  
  /**
   * Set to true on stop,  Thread implementation must stop when this is set to true
   */
  var stopThread = false

  /**
   * Semaphore used by implementation to signal it is ready to operate
   */
  var started = new Semaphore(0);



  /**
   * The message type used to send/receive requests with the application layer
   */
  var messageType = "unknown"

  /**
   * A String describing the low level (network connection + protocol) type.
   * Example: tcp+simple for TCP connection with simple content protocol
   */
  var protocolType = "unknown"

  /**
   * Returns the list of protocol to message type stack supported by this connector.
   *
   * Example: tcp+simple+soap means soap over simple protocol over tcp connection
   *
   * This is used by Connector Factory to create missing connectors when sending messages for example
   *
   */
  def supportedProtocolStack = s"$protocolType+$messageType"

  // User Send Interface
  //----------------------


  /**
   * Used to send to a specific network context peer
   * Typically used by a server side that can handle multiple clients
   */
  def send(data: ByteBuffer, context: NT) : Unit

  /**
   * Returns true if message has been sent
   */
  def send(msg: Message): Boolean

  /**
   * Return true if the connector could send the message
   * Returns false otherwise
   */
  def canHandle(msg: Message): Boolean

  // Lifecycle
  //-----------------

  /**
   * Start a connector by starting a thread
   */
  def lStart = {

    //this.start()
    @->("start")
  }

  /**
   * Stop sets the stopThread class variable to true
   * @listeningPoint stop
   */
  def lStop = {

    this.stopThread = true
    @->("stop")

  }

  /**
   * Holds thread until some credits are available in started semaphore
   *
   * Default Timeout of 0 means unlimited wait
   *
   */
  def waitForStart(timeoutMs: Long = 0) = {

    //-- Acquire and give back 
    timeoutMs match {

      // No timeout
      case t if (t <= 0) =>

        this.started.acquire()
        this.started.release()

      // Timeout 
      case t =>
        this.started.tryAcquire(1, timeoutMs, TimeUnit.MILLISECONDS) match {
          case true =>
            this.started.release()
          case false =>
            throw new RuntimeException(s"Connector start wait timed out (waited $timeoutMs ms)")
        }

    }

  }

  // Run
  //----------------

  /**
   * To be implemented by Server connector
   */
  //override def run

}

/**
 *
 * This Companion object contains some utility conversions
 *
 */
object AbstractConnector {

  object Direction extends Enumeration {
    type Direction = Value
    val Client, Server = Value
  }

  implicit def convertStringToByteBuffer(str: String): java.nio.ByteBuffer = java.nio.ByteBuffer.wrap(str.getBytes)

}

/**
 * Trait to be implemented by objects providing factory for a connector type
 */
trait ConnectorFactory {

  def newInstance(connectionString: String): AbstractConnector[_ <: NetworkContext]

}

object ConnectorFactory {

  var factories = Map[String, ConnectorFactory]()

  // Init
  //--------------------

  //-- Known Factories to this library
  this("tcp+simple", SimpleMessageTCPConnector)

  /**
   * Registers a factory
   */
  def apply(id: String, factory: ConnectorFactory) = {
    this.factories = this.factories + (id -> factory)
  }

  /**
   * Try to instanciate a new connector
   *
   * From context string:
   *
   * - take part before "://"
   * - from "+" separated stack:
   *    - We need two components: low level + protocol
   *    - If more than two, the last one is the message type, so ignore it
   */
  def apply(contextString: String): Option[AbstractConnector[_ <: NetworkContext]] = {

    contextString match {
      case NetworkContext.NetworkString(protocol, message, connectionString) =>

        // Search
        //------------------
        this.factories.get(protocol) match {
          case None =>

            throw new RuntimeException(s"Could not find any Connector for protocol stack: $protocol")

          case Some(connectorFactory) =>

            // Build
            var connector = connectorFactory.newInstance(connectionString)

            // Set Message type
            connector.messageType = message

            Some(connector)
        }

      case _ =>
        
        None
        //throw new RuntimeException(s"Context string does not match format: $contextString")
    }

  }

}

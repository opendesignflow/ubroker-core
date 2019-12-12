
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
package org.odfi.ubroker.core.network.connectors.tcp

import java.nio.channels.ServerSocketChannel
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLContext
import java.security.KeyStore
import java.io.FileInputStream
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.KeyManager
import java.io.File
import java.io.BufferedInputStream
import java.security.cert.CertificateFactory
import org.odfi.tea.io.TeaIOUtils
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.nio.ByteBuffer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import org.odfi.tea.listeners.ListeningSupport
import org.odfi.ubroker.core.network.connectors.AbstractConnector
import org.odfi.ubroker.core.message.Message
import org.odfi.tea.thread.ThreadLanguage

abstract class SSLTCPConnector extends AbstractConnector[TCPNetworkContext] with ListeningSupport with ThreadLanguage {

  private var keyCertificatePairs = List[(File, File)]()

  def addKeyCertificatePair(p: (File, File)) = {
    keyCertificatePairs = keyCertificatePairs :+ p
  }

  /**
   * Create SSL connector
   */
  def buildServerSocketChannel: SSLServerSocket = {

    // Create context
    //--------------
    var context = SSLContext.getInstance("TLSv1")

    // Open keystore
    //var keyFile = new FileInputStream("ssl.key");

    // init keystore
    //------------
    //var ksf = new File("")
    var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null)

    // Load all Key/Certificate Pairs
    //--------------------
    keyCertificatePairs.foreach {
      case (keyFile, certFile) =>

        // Cert
        var ksfis = new FileInputStream(certFile);
        var ksbufin = new BufferedInputStream(ksfis);
        var certificate = CertificateFactory.getInstance("X.509").generateCertificate(ksbufin);

        // Key
        var keyin = new BufferedInputStream(new FileInputStream(keyFile));
        var pkey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(TeaIOUtils.swallowStream(keyin)));

        // set 
        keyStore.setKeyEntry("default", pkey, Array[Char](), Array(certificate))
    }

    // init KeyManagerFactory
    //----------------------
    var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, Array[Char]());

    // init KeyManager
    //var keyManagers[] = keyManagerFactory.getKeyManagers();

    context.init(keyManagerFactory.getKeyManagers, null, null)

    var sslServerSocket = context.getServerSocketFactory.createServerSocket.asInstanceOf[SSLServerSocket]

    //sslServerSocket.

    /*sslServerSocket.setNeedClientAuth(false)
    var c = sslServerSocket.getChannel
    
    println(s"************* SSL channel: "+c)*/

    sslServerSocket
  }

  /**
   * Connection address
   */
  var address = "0.0.0.0"

  /**
   * Connection Port
   */
  var port = 8083

  // Server Runtime Fields
  //-----------

  /**
   * Server Socket used for server connection
   *
   * @group server
   */
  var serverSocket: SSLServerSocket = null

  /**
   * Server Socket Selector
   *
   * @group server
   */
  var serverSocketSelector: Selector = null

  /**
   *
   * Maps a string to te client handler, for backpath matching
   *
   * @group server
   */
  var clientsContextsMap = Map[String, ServerClientConnectionHandler]()

  //var onAcceptConnection   = {  }

  // Client Runtime Fields
  //-----------------

  /**
   * Client Socket to selected Server
   */
  var clientSocket: SocketChannel = null

  /**
   * Client Network Context to selected server
   */
  var clientNetworkContext: TCPNetworkContext = null

  // Send Interface
  //------------------------

  /**
   * Send for server side to client side
   */
  def send(buffer: ByteBuffer, context: TCPNetworkContext) = {

    this.direction match {

      case AbstractConnector.Direction.Client =>

        require(this.clientNetworkContext != null)

        //-- Pass to protocol implementation
        var resBuffer = protocolSendData(buffer, this.clientNetworkContext)

        //-- Send
        this.clientNetworkContext.socket.getOutputStream.write(resBuffer.array())
        this.clientNetworkContext.socket.getOutputStream.flush

      case AbstractConnector.Direction.Server =>

        //-- Pass to protocol implementation
        var resBuffer = protocolSendData(buffer, context)

        //-- Send
        //while (resBuffer.remaining != 0) {
        var written = context.socket.getOutputStream.write(resBuffer.array)
        context.socket.getOutputStream.flush
      //resBuffer.position(written)
      //}

    }

    // println("Sending byte buffer to context")

  }

  /**
   * FIXME
   *
   * Send a message:
   *
   * - Send back to already existing connection
   * - Send through new opening connection
   */
  def send(msg: Message): Boolean = {

    (this.direction, this.clientNetworkContext) match {

      // Server
      //------------------
      case (AbstractConnector.Direction.Server, _) =>

        // Set Message to network context
        //--------
        msg.networkContext.get("message" -> msg)

        this.send(msg.toBytes, msg.networkContext.get.asInstanceOf[TCPNetworkContext])
        true

      // Client
      //--------------

      //-- No Client network context, cannot send
      case (AbstractConnector.Direction.Client, null) =>

        throw new RuntimeException("Client is not connected to Host, maybe something happened at connection")

      //-- Client network connection is there, do it
      case (AbstractConnector.Direction.Client, ctx) =>

        this.send(msg.toBytes, ctx)
        true

    }

  }
  /**
   * This connector can handle the message, if the network context is of the right type, and available
   */
  def canHandle(message: Message): Boolean = {

    message.networkContext match {

      // No context
      case None => false

      // Server, context must be in clients map
      case Some(ctx) if (this.direction == AbstractConnector.Direction.Server) => clientsContextsMap.contains(ctx.toString)

      // Client, host and port must match this one
      case Some(ctx) if (this.direction == AbstractConnector.Direction.Client && this.clientNetworkContext != null) =>

        ctx.qualifier.contains(clientNetworkContext.socket.getInetAddress().asInstanceOf[InetSocketAddress].getHostName() + ":" + clientNetworkContext.socket.getInetAddress().asInstanceOf[InetSocketAddress].getPort()) match {
          case true => true
          case false => false
        }

      /* var NetworkContext.NetworkString(protocol, message, connectionString) = ctx.qualifier
        
         println(s"------ Trying to match qualifier ${ctx.qualifier} ($connectionString , $protocol <-> ${this.protocolType}) against this connectors, target host: "+clientNetworkContext.socket.getRemoteAddress().asInstanceOf[InetSocketAddress].getHostName())
        
        
        ctx.qualifier match {

          case NetworkContext.NetworkString(protocol, message, connectionString) if (protocol == this.protocolType && message == messageType) => 
            
            println(s"----------------> OK")
            true
          case _ => false

        }*/

      case _ => false
    }

  }

  // Protocol Implementation
  //----------------

  def protocolReceiveData(buffer: ByteBuffer, context: TCPNetworkContext): Option[Iterable[Any]]

  /**
   * Calls send Data  through protocol implementation
   *
   * @return A Resulting ByteBuffer to be send over socket
   */
  def protocolSendData(buffer: ByteBuffer, context: TCPNetworkContext): ByteBuffer

  // LifeCycle
  //-------------------

  /**
   * Prepare Socket
   *
   */
  override def lInit = {

    logInfo("Creating Socket")

    // Create Server Socket
    //----------------------------
    /*if (this.direction == AbstractConnector.Direction.Server) {
             this.serverSocket = ServerSocketChannel.open();

        }*/

  }

  /**
   * Start Server Socket Thread
   */
  /*  override def lStart = {

    this.start
    /*
        // Start Server Thread
        //--------------
        if (this.direction == AbstractConnector.Direction.Server) {
            this.start
        }
        // Connect to server as client
        //---------------------
        else {

            logInfo(s"Starting TCP Connector as client on $address:$port")

            // Connect
            this.clientSocket = SocketChannel.open(new InetSocketAddress(this.address,this.port))

            // Record Network Context
            this.clientNetworkContext = new TCPNetworkContext(this.clientSocket)

            logInfo(s"Client Started")

            @->("client.started")

        }*/

  }*/

  override def lStop = {

    // Stop all threads
    this.stopThread = true

    // Stop Server socket
    //------------
    if (this.direction == AbstractConnector.Direction.Server) {

      // Close Selector to stop operations on thread
      if (this.serverSocketSelector != null && this.serverSocketSelector.isOpen) {
        try {
          this.serverSocketSelector.close
        } catch {
          case e: Throwable =>
        }
      }

    } else {

      this.stopThread = true

      try {
        this.clientSocket.close
      } catch {
        case e: Throwable =>
      }

    }

  }
  
  //-- Thread definition
  var thread: Option[Thread] = None

  def getThread = thread.getOrElse {

    var th = createThread {

      run

    }
    th.setDaemon(true)
    thread = Some(th)
    th
  }

  // Connector Run Method
  //------------------------

  //-- React on common started to signal ready to go
  on("common.started") {

    started.release(Integer.MAX_VALUE)
  }

  //-- React on common failed to signal to make sure no thread is blocking
  on("common.start.failed") {

    started.release(Integer.MAX_VALUE)
  }

  class ServerClientConnectionHandler(val networkContext: TCPNetworkContext) extends Thread {
    this.setDaemon(true)

    override def run = {

      // Stop condition
      var stop = false

      try {

        while (!stop) {

          // Prepare read chunks 
          var readArray = new Array[Byte](networkContext.socket.getReceiveBufferSize)

          //-- Read 
          networkContext.socket.getInputStream.read(readArray) match {

            // Continue Reading
            //------------
            case readbytes if (readbytes > 0) => {

              @->("server.read.datas")

              // Pass Datas to underlying protocol
              var readBuffer = ByteBuffer.wrap(readArray)
              //readBuffer.flip

              /*var passedBuffer = ByteBuffer.allocate(readbytes)
                                                passedBuffer.put(readBuffer)
                                                passedBuffer.flip*/
              //println("-> Din")
              var passedBuffer = readBuffer

              //readBuffer.clear
              protocolReceiveData(passedBuffer, networkContext) match {
                case Some(messages) =>

                  //-- Get Message Factory
                  var factory = (Message(SSLTCPConnector.this.messageType), networkContext[String]("message.type")) match {

                    //-- Context Switched factory
                    case (_, Some(contextFactoryName)) =>

                      Message(contextFactoryName) match {
                        case Some(factory) => factory
                        case None =>
                          throw new RuntimeException(s"TCP network context is configured with ${contextFactoryName} message type which has no registered factory")

                      }

                    //-- Normal Connector default factory
                    case (Some(factory), None) => factory

                    case _ =>
                      throw new RuntimeException(s"TCP Connector is configured with ${SSLTCPConnector.this.messageType} message type which has no registered factory")

                  }

                  //-- Create Messages
                  messages.foreach {
                    m =>

                      //println("[Server] Got message: "+new String(m.asInstanceOf[ByteBuffer].array()))
                      //logInfo[SSLTCPConnector]("[Server] Got message: "+new String(m.asInstanceOf[ByteBuffer].array()))
                      // Create Message
                      var message = factory(m)

                      // Append context
                      message.networkContext = Some(networkContext)

                      // Send
                      SSLTCPConnector.this.network.dispatch(message)
                 
                  }

                // Protocol not ready
                case None =>

              }

              // Clear Buffer for next read
              //readBuffer.flip()
              // continue = false
              //readBuffer.clear();
            }

            // Nothing to read
            //----------------
            case readbytes if (readbytes == 0) => {

              @->("server.read.nodatas")
              //readBuffer.clear();
              // continue = false
            }

            // Close Client Connection
            //------------
            case readbytes if (readbytes < 0) => {

              @->("server.read.close")

              logFine[SSLTCPConnector]("-- Closing Connection")
              stop = true

              networkContext.@->("close")
              clientsContextsMap -= networkContext.toString
              networkContext.socket.close();

            }
          }
        }
        // EOF loop

      } catch {
        case e: Throwable =>
      }

    }
  }

  /**
   * Start this connector in Listening mode if in SERVER direction,
   * or tries to connect to  target address if in CLIENT direction
   *
   */
  def run = {

    // Common
    //---------------

    //-- Prepare Read Buffer
    //----------------

    // Server Mode
    //-------------------
    if (this.direction == AbstractConnector.Direction.Server) {

      @->("server.start")

      logInfo[SSLTCPConnector](s"Starting TCP Connector on $address:$port (${this.messageType})")

      // Bind
      //--------------
      this.serverSocket = buildServerSocketChannel
      this.serverSocket.bind(new InetSocketAddress(address, port))

      // Register Selector for all operations
      // !! Selector only works on non blocking sockets
      //----------------------
      //this.serverSocketSelector = Selector.open()
      // this.serverSocket.configureBlocking(false);
      //   this.serverSocket.register(this.serverSocketSelector, SelectionKey.OP_ACCEPT)

      //this.serverSocket.setOption(java.net.StandardSocketOptions.SO_RCVBUF, 4096)

      // Loop on Selection and handle actions
      //------------------
      @->("server.started")
      @->("common.started")
      while (!this.stopThread) {

        try {

          // Accept connection
          //--------------------
          logFine[SSLTCPConnector](s"-- Waiting for something to happen on selector thread $address:$port")
          this.serverSocket.accept() match {

            //-- Handle stop 
            case null =>
              this.stopThread = true

            case clientSocket =>

              // Create Client context
              //------------------
              logFine[SSLTCPConnector]("-- Accepting Connection on $address:$port ")
              try {
                var networkContext =
                  new TCPNetworkContext(clientSocket)

                @->("server.accepted")
                @->("server.accepted", networkContext)

                // Create Thread to handle that
                //------------------
                var handler = new ServerClientConnectionHandler(networkContext)
                handler.start()

                networkContext.qualifier = s"client@${networkContext.hashCode}"
                clientsContextsMap += (networkContext.toString -> handler)

              } catch {
                // Error while accepting connection -> live on
                case e: Throwable =>
                  e.printStackTrace()
              }

          }

        } catch {
          case e: Throwable =>
            this.stopThread = true
            e.printStackTrace()
        }

      } // EOF Server thread loop

      // Clean
      //----------------
      this.serverSocketSelector = null

      this.serverSocket.close
      this.serverSocket = null

      @->("server.end")

    } // Client Mode
    //------------------------
    else {

      logInfo[SSLTCPConnector](s"Starting TCP Connector as client on $address:$port")

      // Connect
      var addr = new InetSocketAddress(InetAddress.getByName(this.address), this.port)
      try {
        this.clientSocket = SocketChannel.open(addr)
      } catch {
        case e: Throwable =>

          logError[SSLTCPConnector](s"Failed connection to ${addr} and port $port, not resolved: ${addr.isUnresolved()}")

          // Make sure started signal has been given
          @->("common.start.failed")

          throw e
      }
      // Record Network Context
      this.clientNetworkContext = new TCPNetworkContext(this.clientSocket)
      // this.clientNetworkContext.qualifier = s"server@${this.clientNetworkContext.hashCode}"

      logInfo[SSLTCPConnector](s"Client Started")

      @->("client.started")
      @->("common.started")

      // Data Loop
      //------------------------
      var readBuffer = ByteBuffer.allocate(4096) // (Buffer of a page size per default)
      var continue = true;
      while (continue && !this.stopThread)
        try {
          this.clientSocket.read(readBuffer) match {

            // Continue Reading
            //------------
            case readbytes if (readbytes > 0) => {

              // Pass Datas to underlying protocol
              readBuffer.flip

              /* var passedBuffer = ByteBuffer.allocate(readbytes)
              passedBuffer.put(readBuffer)
              passedBuffer.rewind*/

              protocolReceiveData(readBuffer, clientNetworkContext) match {
                case Some(messages) =>

                  // Get Message Factory
                  Message(this.messageType) match {

                    case Some(factory) =>

                      logInfo[SSLTCPConnector](s"[Client] Parsing messages (${messages.size}) found from protocol ")
                      messages.foreach {
                        m =>

                          logInfo[SSLTCPConnector](s"[Client] Go")
                          //logInfo[SSLTCPConnector]("[Client] Got message: " + new String(m.asInstanceOf[ByteBuffer].array()))

                          // Create Message
                          var message = factory(m)

                          // Append context
                          message.networkContext = Some(clientNetworkContext)

                          // Send
                          this.network.dispatch(message)
                      }

                    case None =>
                      throw new RuntimeException(s"TCP Connector is configured with ${this.messageType} message type which has no registered factory")

                  }

                // Protocol not ready
                case None =>

              }

              // protocolReceiveData(passedBuffer, this.clientNetworkContext)

              // Clear Buffer for next read
              readBuffer.flip();
            }

            // Close Client Connection
            //------------
            case readbytes if (readbytes < 0) => {
              this.clientSocket.close();
              continue = false
              logInfo[SSLTCPConnector](s"[Client] EOF socket")
            }
          }

        } catch {

          // In case of I/O Exception, stop
          case e: java.io.IOException =>
            continue = false

            logInfo[SSLTCPConnector](s"[Client] IOException: " + e.getLocalizedMessage())

          // Otherwise let live
          case e: Throwable =>
            continue = false
            e.printStackTrace()
        }

      // EOF Data -> Clean
      //------------------
      this.clientNetworkContext = null
      this.clientSocket = null

    }
  }

}

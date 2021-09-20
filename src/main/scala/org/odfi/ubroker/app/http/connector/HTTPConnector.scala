/*
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
package org.odfi.ubroker.app.http.connector

import org.odfi.ubroker.core.network.connectors.tcp.TCPNetworkContext
import org.odfi.ubroker.core.network.connectors.tcp.TCPProtocolHandlerConnector
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.tea.listeners.ListeningSupport
import java.nio.ByteBuffer
import org.odfi.ubroker.core.network.protocols.ProtocolHandler
import org.odfi.ubroker.core.message.Message
import org.odfi.ubroker.core.network.connectors.tcp.TCPNetworkContext
import org.odfi.ubroker.core.network.connectors.tcp.TCPProtocolHandlerConnector
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.ubroker.app.http.message.HTTPRequest
import org.odfi.ubroker.app.mime.DefaultMimePart
import org.odfi.ubroker.app.mime.MimePart
import org.odfi.tea.logging.TLogSource
import org.odfi.ubroker.app.http.connector.websocket.WebsocketProtocolhandler
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.ubroker.app.http.message.HTTPMessage
import org.odfi.ubroker.app.http.message.HTTPResponse
import org.odfi.ubroker.core.network.connectors.ConnectorFactory
import org.odfi.ubroker.core.network.connectors.AbstractConnector
import java.net.URL
import org.odfi.ubroker.core.network.connectors.tcp.SSLTCPProtocolHandlerConnector

class HTTPConnector(cport: Int) extends TCPProtocolHandlerConnector[MimePart](ctx => new HTTPProtocolHandler(ctx)) with TLogSource {

  this.address = "0.0.0.0"
  this.port = cport
  this.messageType = "http"
  this.protocolType = "tcp+http"

  Message("http", HTTPMessage)

  /**
   * After sending response data to a client, one must close the socket
   */
  override def send(buffer: ByteBuffer, context: TCPNetworkContext) = {
    super.send(buffer, context)
    //context.socketChannel.socket().flu
    /*logInfo {
      "Send datas to client -> close it"
    }*/

    //println("Closing")

    //context.socketChannel.close()

    //context.socket.shutdownOutput()

    //println("Send datas to client -> close it")
    //context.socket.close
    //context.socket.socket.close

  }

}

class HTTPSConnector(cport: Int) extends SSLTCPProtocolHandlerConnector[MimePart](ctx => new HTTPProtocolHandler(ctx)) with TLogSource {

  this.address = "0.0.0.0"
  this.port = cport
  this.messageType = "http"
  this.protocolType = "tcp+https"

  Message("http", HTTPMessage)

  /**
   * After sending response data to a client, one must close the socket
   */
  override def send(buffer: ByteBuffer, context: TCPNetworkContext) = {
    super.send(buffer, context)

  }

}

object HTTPSConnector extends ConnectorFactory {

  /**
   * Registers classes in various factories
   */
  def init = {

    ConnectorFactory("tcp+https", this)

  }

  def apply(port: Int): HTTPSConnector = new HTTPSConnector(port)

  /**
   * Factory for Client mode connectors
   */
  def newInstance(connectionString: String): AbstractConnector[_ <: NetworkContext] = {

    //-- Parse Conn String URL (Connection string has no protocol anymore, so add it to use the URL class)
    var url = new URL("https://" + connectionString)

    //-- Create connector
    var connector = new HTTPSConnector(443)
    connector.address = url.getHost()
    //var connString = new

    connector

  }
}

object HTTPConnector extends ConnectorFactory {

  /**
   * Registers classes in various factories
   */
  def init = {

    ConnectorFactory("tcp+http", this)

  }

  def apply(port: Int): HTTPConnector = new HTTPConnector(port)

  /**
   * Factory for Client mode connectors
   */
  def newInstance(connectionString: String): AbstractConnector[_ <: NetworkContext] = {

    //-- Parse Conn String URL (Connection string has no protocol anymore, so add it to use the URL class)
    var url = new URL("http://" + connectionString)

    //-- Create connector
    var connector = new HTTPConnector(80)
    connector.address = url.getHost()
    //var connString = new

    connector

  }

}

class HTTPProtocolHandler(var localContext: NetworkContext) extends ProtocolHandler[MimePart](localContext) with ListeningSupport with TLogSource {

  // Receive
  //-----------------

  /**
   * Read lines or bytes depending on data received
   *
   * Supported:
   *
   * line or bytes
   */
  var readMode = "line"

  /**
   * The read mode for upcoming message part
   */
  var nextReadMode = "line"

  var contentLength = 0

  var contentTypeRegexp = """Content-Type: (.*)""".r
  var contentLengthRegexp = """Content-Length: (.*)""".r

  // Take Lines and create message
  var currentPart = new DefaultMimePart()

  // Protocol Switching
  //-------------------------
  var switchingProtocols = Map[String, Class[_ <: ProtocolHandler[_]]]("websocket" -> classOf[WebsocketProtocolhandler])

  // Chunked Transfer Encoding
  //-------------------------------

  //-- Stays to actual expected chunk size accross calls
  var chunkSize = 0

  var transferEncodingRegexp = """Transfer-Encoding: (.*)""".r

  // Send
  //---------------------

  def storePart(part: MimePart) = {

    (this.availableDatas.contains(this.currentPart), this.availableDatas.size) match {

      // Part is already stacked, don't do anything
      case (true, size) =>
        logFine[HTTPConnector]("Part is already stacked, don't do anything")
      // Add part
      case (false, 0) =>

        logFine[HTTPConnector]("Storing mime part")
        this.availableDatas += part

      // Merge part
      case (false, size) =>

        logFine[HTTPConnector]("Merging with head")
        this.availableDatas.head.append(part)
    }

  }

  /**
   * REceive some HTTP
   */
  def receive(buffer: ByteBuffer): Boolean = {

    @->("http.connector.receive", buffer)
    var bytes = new Array[Byte](buffer.remaining)
    buffer.get(bytes)

    logFine[HTTPConnector]("Got HTTP Datas: " + new String(bytes))

    // Use SOurce to read from buffer
    //--------------------
    //var bytes  = bytesArray
    //var bytesSource = Source.fromInputStream(new ByteArrayInputStream(buffer.array))
    var stop = false

    while ({ {

      // If no bytes to read, put on hold
      if (bytes.size == 0)
        stop = true
      else
        // Read Mode
        //------------------
        readMode match {

          // Take line
          //---------------
          case "line" =>

            //  Read line
            //---------------
            var currentLineBytes = bytes.takeWhile(_ != '\n')
            bytes = bytes.drop(currentLineBytes.size + 1)
            if (bytes.length != 0 && bytes(0) == '\r')
              bytes.drop(1)

            var line = new String(currentLineBytes.toArray).trim

            // If Some content is expected,

            //  Read line
            /*var currentLineBytes = bytes.takeWhile(_ != '\n')
            bytes = bytes.drop(currentLineBytes.size + 1)
            var line = new String(currentLineBytes.toArray).trim*/

            //-- Parse protocol
            //-------------------------
            line match {

              //-- Content Length expected : Switch to bytes buffereing
              case line if (contentLengthRegexp.findFirstMatchIn(line) match {
                case Some(matched) =>

                  contentLength = matched.group(1).toInt
                  logFine[HTTPConnector]("Content Type specified to bytes")
                  nextReadMode = "bytes"
                  true

                case None => false
              }) =>

                currentPart.addParameter(line)

              //-- Content Type
              case line if (contentTypeRegexp.findFirstMatchIn(line) match {

                // Multipart form data -> just continue using lines
                case Some(matched) if (matched.group(1).matches("multipart/form-data.*")) => true
                case Some(matched) if (matched.group(1).matches("application/x-www-form-urlencoded.*")) => true

                // Otherwise, don't change anything
                case Some(matched) =>

                  true

                case None => false
              }) =>

                currentPart.addParameter(line)

              //-- Transfer-Encoding Chunked
              case line if (transferEncodingRegexp.findFirstMatchIn(line) match {

                // Chunked
                case Some(matched) if (matched.group(1).matches("chunked")) =>

                  logFine[HTTPConnector]("Content is chunked")
                  nextReadMode = "chunked"
                  true

                // Unsupported
                case Some(matched) =>
                  logFine[HTTPConnector]("Unsupported Transfer-Encoding: " + matched.group(1))
                  true

                case None =>
                  false
              }) => currentPart.addParameter(line)

              //-- Normal Line
              case line if (line != "") =>

                currentPart.addParameter(line)

                //-- If content lenght is reached, that was the last line
                if (contentLength != 0 && contentLength == currentPart.contentLength) {
                  this.storePart(this.currentPart)
                  this.currentPart = new DefaultMimePart
                  this.contentLength = 0

                }

              //-- Empty Line but content is upcomming
              case line if (line == "" && contentLength != 0 && nextReadMode == "line") =>

                logFine[HTTPConnector](s"Empty Line but some content is expected")

                //--> Write this message part to output
                this.availableDatas += this.currentPart
                this.currentPart = new DefaultMimePart

              //-- Empty line, content is upcoming and next Read mode is not line
              case line if (line == "" && nextReadMode != "line") =>

                logFine[HTTPConnector](s"Empty Line but some content is expected in read mode: $nextReadMode, for a length of: $contentLength")
                readMode = nextReadMode

              //-- Empty Line and no content
              case line if (line == "" && contentLength == 0 && this.currentPart.contentLength > 0) =>

                logFine[HTTPConnector](s"Empty Line and no content expected, end of section")

                //--> Write this message part to output
                this.availableDatas += this.currentPart
                this.currentPart = new DefaultMimePart
                this.contentLength = 0

              case _ =>
            }

          // Buffer Bytes
          //---------------
          case "bytes" =>

            // Read
            this.currentPart += bytes
            bytes = bytes.drop(bytes.size)

            // Report read progress 
            var progress = this.currentPart.bytes.size * 100.0 / contentLength
            logFine[HTTPConnector](s"Read state: $progress %, $contentLength expected, and read bytes ${this.currentPart.bytes.size} and content length: ${this.currentPart.contentLength} ")
            if ((contentLength - this.currentPart.contentLength) < 10) {
              //if ( progress == 100 ) {

              (this.availableDatas.contains(this.currentPart), this.availableDatas.size) match {

                // Part is already stacked, don't do anything
                case (true, size) =>

                // Add part
                case (false, 0) =>

                  logFine[HTTPConnector]("Storing mime part")
                  this.availableDatas += this.currentPart

                // Merge part
                case (false, size) =>

                  logFine[HTTPConnector]("Merging with head")
                  this.availableDatas.head.append(this.currentPart)
              }

              // Reset all
              this.currentPart = new DefaultMimePart
              this.contentLength = 0
              this.nextReadMode = "line"
              this.readMode = "line"

            }

          //-- Chunked
          case "chunked" =>

            while ({ {

              //-- If Chunk size is 0 -> try to determine a size from first line
              chunkSize = chunkSize match {
                case 0 =>

                  //-- Read Size Line as HEX value
                  var chunksizeLine = bytes.takeWhile(_.toChar != '\n').map(_.toChar).mkString.trim
                  bytes = bytes.drop(chunksizeLine.getBytes.length + 2) // 2 for CRLF
                  Integer.parseInt(chunksizeLine, 16)

                case _ => chunkSize
              }

              //-- Normal processing
              chunkSize match {

                // END
                case 0 =>

                  logFine[HTTPConnector](s"[Chunked] End of chunked transfer")
                  storePart(this.currentPart)
                  stop = true

                  // Reset all
                  this.currentPart = new DefaultMimePart
                  this.contentLength = 0
                  this.nextReadMode = "line"
                  this.readMode = "line"

                case _ =>

                  //-- Read bytes and add to mime part
                  var readBytes = bytes.size match {

                    case available if (available >= chunkSize) =>

                      // Get byte, then drop from array + 2 bytes for the last CRLF
                      logFine[HTTPConnector](s"[Chunked] Reading Chunk of $chunkSize")
                      this.currentPart += bytes.take(chunkSize)
                      bytes = bytes.drop(chunkSize + 2)

                      // Chunk size to 0 to reset actual chunk
                      chunkSize = 0

                    case available =>

                      // Get as much as possible until next call
                      logFine[HTTPConnector](s"[Chunked] Not enough bytes available (${bytes.size} to complete chunk of $chunkSize")

                      this.currentPart += bytes.take(available)
                      bytes = bytes.drop(chunkSize + 2)

                      // Update chunksize ot remaining
                      chunkSize = chunkSize - available

                      stop = true

                      available
                  }
              }

            } ; !stop}) ()

          case mode => throw new RuntimeException(s"HTTP Receive protocol unsupported read mode: $mode")

        }

    } ; !stop}) ()

    logFine[HTTPConnector]("Done")
    true

  }

  /**
   * Send Buffer May Support connection upgrade
   */
  def send(buffer: ByteBuffer, nc: NetworkContext): ByteBuffer = {

    // Try to detecte protocol switching
    //----------------
    nc[HTTPResponse]("message") match {
      case Some(httpResponse) =>

        (httpResponse.parameters.find(_._1 == "Connection"), httpResponse.parameters.find(_._1 == "Upgrade")) match {

          //-- Upgrade to Websocket
          case (Some(Tuple2(_, "Upgrade")), Some(Tuple2(_, "websocket"))) =>

            //println(s"Connector Update to websocket protocol detected")

            // Record in Network Context the new protocol handler
            nc("protocol.handler" -> new WebsocketProtocolhandler(nc))
            nc("message.type" -> "json-soap")

          case _ =>
        }

      case _ =>
    }

    // Normal Send
    //-------------------

    var post = "\r\n"
    post = ""

    post.getBytes.length match {
      case 0 =>
        buffer
      case l =>
        var newBuffer = ByteBuffer.allocate(buffer.remaining() + post.getBytes().length)

        newBuffer.put(buffer)
        buffer.clear()
        newBuffer.put(post.getBytes())
        newBuffer.flip()
        newBuffer
    }

    // buffer
    /*var newBuffer = ByteBuffer.allocate(buffer.remaining())
    newBuffer.put(buffer)
    newBuffer.flip()))))
    newBuffer*/

  }

}

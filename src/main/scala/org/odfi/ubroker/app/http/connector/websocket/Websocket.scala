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
package org.odfi.ubroker.app.http.connector.websocket

import java.net.ProtocolException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException

import scala.jdk.CollectionConverters._

import org.odfi.tea.logging.TLogSource
import org.odfi.ubroker.core.network.NetworkContext
import org.odfi.ubroker.core.network.connectors.tcp.TCPNetworkContext
import org.odfi.ubroker.core.network.protocols.ProtocolHandler

class WebsocketProtocolhandler(var localContext: NetworkContext) extends ProtocolHandler[ByteBuffer](localContext) with TLogSource {

  val bytesDescription = RegisterFile(WebsocketProtocolhandler.protocolDescription)
  val sendDescription = RegisterFile(WebsocketProtocolhandler.protocolDescription)

  /**
   * Count of remaining datas, if 0, we are processing normal message, not waiting for payload to be completed
   */
  var remainingDatas = 0

  var payloadLength: Int = 0

  var maskingKey: Option[Array[Int]] = None

  var readDatas: ByteBuffer = _

  def receive(buffer: ByteBuffer): Boolean = {

    var stop = false

    logFine[WebsocketProtocolhandler](s"Received some Websocket datas, with ordering: ${buffer.order().toString()} (native: ${java.nio.ByteOrder.nativeOrder()}), capacity: " + buffer.remaining())

    //buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
    while ({ {
      remainingDatas match {

        // Message Mode
        //--------------------
        case 0 =>

          // Take the first 4 Bytes and analyse using header
          //---------------------------
          var header = bytesDescription.register("header")
          header.value = buffer.getInt()

          //header.explainMemory

          logFine[WebsocketProtocolhandler]("After header remaining: " + buffer.remaining())

          // !!
          // !! The ordering of following code is mandatory (because of buffer sequential reading)
          // !!

          // Get Payload Length
          //-------------------
          payloadLength = header.field("PayloadLen").value match {

            // Last two bytes of Header Bytes are not used as payload length, so rewind by 2
            case l if (l < 126) =>

              buffer.position(buffer.position() - 2)
              l.toInt

            // Take last two bytes of header as length
            case l if (l == 126) => (header.field("ExtraPayloadLen").value).toInt

            // Use ExtraPayloadLen and Extended PayLoad len as one register to concatenate the bytes
            case _ =>

              var extendedLength = bytesDescription.register("ExtendedPayloadLen")

              //-- Rewind 2 bytes in buffer, and read a Long 
              buffer.position(buffer.position() - 2)
              extendedLength.value = buffer.getLong()

              extendedLength.value.data.toInt
          }

          logFine[WebsocketProtocolhandler](s"After payload length ($payloadLength): " + buffer.remaining())

          // Get Masking Key Octets
          //-----------------------
          maskingKey = header.field("MASK").value match {

            //-- Read 4 bytes of masking key
            case 1 =>
              var key = buffer.getInt

              logFine[WebsocketProtocolhandler](s"Masking key: " + key.toHexString)

              var keyOctets = Array((key >> 0) & 0xFF, (key >>> 8) & 0xFF, (key >>> 16) & 0xFF, (key >>> 24) & 0xFF)

              //-- Reverse Bytes if ordering of system and buffer differs
              buffer.order() match {
                case order if (order != java.nio.ByteOrder.nativeOrder()) => Some(keyOctets.reverse)
                case _ => Some(keyOctets)
              }

            case 0 =>

              None
          }

          logFine[WebsocketProtocolhandler]("After key length: " + buffer.remaining())

          // Handle data type
          //--------------------------
          logFine[WebsocketProtocolhandler]("OPCODE: " + header.field("OPCODE").value)
          header.field("OPCODE").value match {

            case WebsocketProtocolhandler.OpCode.CONTINUATION =>
            case WebsocketProtocolhandler.OpCode.TEXT_FRAME =>

            //println(s"Found Text Data of length: " + payloadLength)
            //println(s"Countent: " + new String(payLoad.map { v => v.toChar }))

            case WebsocketProtocolhandler.OpCode.PING =>
            case WebsocketProtocolhandler.OpCode.PONG =>
            case WebsocketProtocolhandler.OpCode.BINARY_FRAME =>
            case WebsocketProtocolhandler.OpCode.CLOSE =>
              //this.context.asInstanceOf[TCPNetworkContext].socket.close()
              remainingDatas = 0
              payloadLength = 0

              throw new ClosedChannelException
            case code => throw new ProtocolException(s"OPCCODE $code is not handled")

          }

          // Get PayLoad
          //-----------------
          try {
            //-- Prepare output Buffer
            this.readDatas = ByteBuffer.allocate(payloadLength)
            remainingDatas = payloadLength - buffer.remaining()

            //-- Read Data up to buffer payload length
            remainingDatas match {

              //-- all data here
              case diff if (diff < 0) =>

                //remainingDatas = remainingDatas - remain
                //println(s"Reading data to readDatas (size=${readDatas.capacity()}), length=$payloadLength")

                var receivingArray = new Array[Byte](payloadLength)
                var readBuffer = buffer.get(receivingArray)

                this.readDatas.clear()

               //println(s"Reading data to readDatas (size=${readDatas.capacity()}), length in readBuffer=${readBuffer.remaining()}, length in readArray=${receivingArray.length}")

                this.readDatas.put(receivingArray)
                remainingDatas = 0
              //-- Not enough data
              case remain =>

                this.readDatas.put(buffer)

              //this.readDatas.put(buffer.get(new Array[Byte](remainingDatas)))
              //   remainingDatas = 0

            }

            //

            //this.readDatas.put(buffer.get(new Array[Byte](payloadLength)))
            //this.readDatas.put(buffer)

          } catch {
            case e: java.nio.BufferOverflowException =>
              println(s"Error during payload read")
              e.printStackTrace(System.out)
          }

        // Data Receive mode
        //---------------------------
        case _ =>

          //-- Read
          logFine[WebsocketProtocolhandler](s"Receiving data, remaining: $remainingDatas , buffer has: ${buffer.remaining()},")
          buffer.remaining() match {

            //-- less than needed
            case remain if (remain <= remainingDatas) =>

              remainingDatas = remainingDatas - remain
              this.readDatas.put(buffer)

            //-- More or enough
            case remain if (remain > remainingDatas) =>

              var receivingArray = new Array[Byte](remainingDatas)
              var readBuffer = buffer.get(receivingArray)

              this.readDatas.put(receivingArray)
              remainingDatas = 0

          }

      }

      // Finish Datas
      //------------------
      remainingDatas match {
        case 0 =>

          //var array = new Array[Byte](payloadLength)
          // readDatas.flip()
          var array = readDatas.array

          logFine[WebsocketProtocolhandler](s"Decoding length: " + array.length)

          var payLoad = maskingKey match {

            //-- Decode Through key XOR buffer
            case Some(keyOctets) =>

              // Map to char to avoid Signed Byte logic, and reverse the bytes which are in the wrong order
              // var intArray = array.map { _.toInt match { case v if (v < 0) => ~(v);case v => v } }

              //-- Reverse Bytes if ordering of system and buffer differs
              var intArray = buffer.order() match {
                case order if (order != java.nio.ByteOrder.nativeOrder()) => array.map { v => (v & 0xFF) }
                case _ => array.map { v => (v & 0xFF) }
              }

              WebsocketProtocolhandler.unmask(intArray, keyOctets)

            //-- Don't decode
            case None =>

              buffer.order() match {
                case order if (order != java.nio.ByteOrder.nativeOrder()) => array.reverse.map { v => (v & 0xFF) }
                case _ => array.map { v => (v & 0xFF) }
              }

          }

          //-- Stack results
          //println(s"Countent: " + new String(payLoad.map { v => v.toChar }))
          this.availableDatas += (ByteBuffer.wrap(payLoad.map { v => v.toByte }))

          logFine[WebsocketProtocolhandler](s"Stacked: " + new String(this.availableDatas.head.array()))

          payloadLength = 0
        case _ =>
      }

      stop = buffer.remaining() == 0
      
      logFine[WebsocketProtocolhandler](s"Stopping: " + stop)
      //var res = (remainingDatas == 0)

      // println(s"Remaining datas to wait for: " + remainingDatas + "//" + res)

      //res

    } ; !stop}) ()

    true

  }

  def send(buffer: ByteBuffer, nc: NetworkContext): ByteBuffer = {

    logFine[WebsocketProtocolhandler](s"Sending Websocket for length of: " + buffer.remaining)

    //-- Prepare Header
    //-------------------------
    var head = sendDescription.register("header")
    head.value = 0

    head.field("FIN").value = 1

    //-- Send text
    head.field("OPCODE").value = 0x1

    //-- Length
    var extendedPayloadLength = 0L
    var lengthExtraBytes = buffer.remaining().toLong match {

      // Length on 7 bits
      case payloadLength if (payloadLength < 127) =>

        logFine[WebsocketProtocolhandler](s"Length on 7 bits")

        head.field("PayloadLen").value = payloadLength
        0

      // Length on 16 bits
      case payloadLength if (payloadLength < Math.pow(2, 16)) =>

        logFine[WebsocketProtocolhandler](s"Length on 16 bits")

        head.field("PayloadLen").value = 126
        head.field("ExtraPayloadLen").value = payloadLength
        2

      // Otherwise 64 bits
      case payloadLength =>

        logFine[WebsocketProtocolhandler](s"Length on 64 bits")

        head.field("PayloadLen").value = 127
        extendedPayloadLength = payloadLength

        8

    }

    //-- Header length: 2 bytes + eventual extra payloadlength
    var totalLength = 2 + lengthExtraBytes + buffer.remaining()

    // Write Message to new Buffer
    //----------------
    var outBuffer = ByteBuffer.allocate(totalLength)
    //outBuffer.order(ByteOrder.BIG_ENDIAN)

    //head.explainMemory

    logFine[WebsocketProtocolhandler](s"Head byte 0 (payload +length: ${(head.value >>> 8).toBinaryString}")
    outBuffer.put(((head.value >>> 24) & 0xFF).toByte)

    outBuffer.put(((head.value >>> 16) & 0xFF).toByte)

    //-- First output length because of endianness
    lengthExtraBytes match {
      case 2 =>
        outBuffer.put(((head.value >>> 8) & 0xFF).toByte)
        outBuffer.put(((head.value >>> 0) & 0xFF).toByte)
      case 8 =>
        logFine[WebsocketProtocolhandler](s"64bits length: " + extendedPayloadLength)
        outBuffer.putLong(extendedPayloadLength)
      case _ =>
    }

    outBuffer.put(buffer);
    outBuffer.flip()
    outBuffer

    // outBuffer.put(buffer);

  }

}
object WebsocketProtocolhandler {

  val protocolDescription = <RegisterFile>
                              <!-- 4 Bytes -->
                              <Register name="header">
                                <Field name="ExtraPayloadLen" width="16"></Field>
                                <Field name="PayloadLen" width="7"></Field>
                                <Field name="MASK" width="1"></Field>
                                <Field name="OPCODE" width="4"></Field>
                                <Field name="RSV3" width="1"></Field>
                                <Field name="RSV2" width="1"></Field>
                                <Field name="RSV1" width="1"></Field>
                                <Field name="FIN" width="1"></Field>
                              </Register>
                              <!-- 8 bytes : Combines with 6 read bytes and remaining two of header -->
                              <Register name="ExtendedPayloadLen">
                                <Field name="ExtraPayloadLen" width="16"></Field>
                                <Field name="ExtendedPayloadLen" width="48"></Field>
                              </Register>
                              <Register name="MaskingKey">
                                <Field name="MaskingKey" width="32"></Field>
                              </Register>
                            </RegisterFile>

  object OpCode extends Enumeration {
    type OpCode = Int

    val CONTINUATION = 0x0
    val TEXT_FRAME = 0x1
    val BINARY_FRAME = 0x2
    val RSV3 = 0x3
    val RSV4 = 0x4
    val RSV5 = 0x5
    val RSV6 = 0x6
    val RSV7 = 0x7
    val CLOSE = 0x8
    val PING = 0x9
    val PONG = 0xA
  }

  def unmask(bytes: Array[Int], keyOctets: Array[Int]): Array[Int] = {

    // Map to char to avoid Signed Byte logic, and reverse the bytes which are in the wrong order
    // var intArray = array.map { _.toInt match { case v if (v < 0) => ~(v); case v => v } }
    //var intArray = bytes.map { v => (v & 0xFF) }
    //var intArray = bytes

    // Decode
    // j                   = i MOD 4
    // transformed-octet-i = original-octet-i XOR masking-key-octet-j
    /*for (i <- 0 to (intArray.length - 1)) {

      var keyOctet = keyOctets(i % 4)

      //println(s"Doing XOR of ${intArray(i).toHexString} // ${intArray(i)} with ${keyOctet.toHexString}")

      //-- XOR
      intArray(i) = (intArray(i) ^ keyOctet)

      // println(s"Doing XOR of ${intArray(i).toHexString} with ${keyOctet.toHexString}")

    }*/

    var resArray = for (i <- 0 to (bytes.length - 1)) yield (bytes(i) ^ keyOctets(i % 4))

    resArray.toArray

    // intArray

  }

}

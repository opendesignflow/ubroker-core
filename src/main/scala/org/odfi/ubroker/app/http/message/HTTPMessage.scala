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
package org.odfi.ubroker.app.http.message

import org.odfi.ubroker.app.mime._
import org.odfi.ubroker.core.message._
import org.odfi.ubroker.app.http.session._
import java.nio._
import org.odfi.ubroker.app.mime.MimePart
import org.odfi.tea.logging.TLogSource
import org.odfi.ubroker.core.network.connectors.tcp.TCPNetworkContext
import org.odfi.ubroker.core.network.NetworkContext
import java.net.URL
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.io.ByteArrayOutputStream
import org.w3c.dom.html.HTMLElement
import java.io.InputStreamReader
import java.io.BufferedReader

trait HTTPMessage extends Message {

  
  /**
   * The current session if available
   */
  var session: Option[Session] = None

  def getSession = session

  def hasSession = session.isDefined

  // Cookies
  //----------
  var cookies = Map[String, String]()

}

object HTTPMessage extends MessageFactory {

  def apply(data: Any): HTTPMessage = {

    var part = data.asInstanceOf[MimePart]

    //println(s"Got http message for factory: " + part.protocolLines)

    //-- Request or Response?
    part.protocolLines(0) match {
      case response if (response.startsWith("HTTP")) => HTTPResponse(data)
      case request => HTTPRequest(data)
    }

  }
}

/**
 *
 * Qualifier: s"http:$path:$operation"
 * Example: http:/index:GET
 *
 * @author rleys
 *
 */
class HTTPRequest(

  var operation: String,
  var path: String,
  var version: String) extends MimePart with HTTPMessage with TLogSource {

  // Path and URL parameters separation
  //---------------------

  var originalPath = this.path.split("""\?""").head
  def originalURL = "http://" + (this.getParameter("Host").get + "/" + originalPath).replace("//", "/")

  //-- Path may contain some URL encoded parameters, decode them
  //-----------

  var urlParameters = scala.collection.mutable.Map[String, String]()
  path.split('?') match {
    case split if (split.length==1) => 
    case split => 
      val queryPart = split.last
      queryPart.split("&").foreach {
        parameterString =>

          val splitValue = parameterString.split("=")
          val name = splitValue(0)
          val value = splitValue.length match {
            case 1 => ""
            //-- If a value is set, remove name from array and reconstruct in case the value has unencoded "="
            case other => java.net.URLDecoder.decode(splitValue.drop(1).mkString("="), "UTF-8")
          }
          urlParameters.update(name, value)
      }
  }
 

  //-- Allow removing url parameters
  def removeURLParameter(name: String) = this.urlParameters.get(name) match {
    case None =>
    case Some(v) =>
       this.urlParameters.remove(name)
  }

  //-- Ensure path has no URL parameters
  this.path = this.path.split("""\?""").head

  // Do this now and not earlier to avoid mangling of the path values (GET)
  path = path.replaceAll("//+", "/")

  // Use Path as qualifier
  this.qualifier = s"http:$path:$operation"

  def getCurrentURL = ("http://" + this.getParameter("Host").get + "/" + this.path).replaceAll("//+", "/")

  /**
   * Update the path
   * Ensures the path always has a starting "/"
   */
  def changePath(newPath: String) = {
    newPath.startsWith("/") match {
      case true => this.path = newPath
      case false => this.path = "/" + newPath
    }

    this.qualifier = s"http:${this.path}:$operation"
  }

  /**
   * Changes the message path by removing a prefix
   */
  def stripPathPrefix(prefix: String) = {
    changePath(path.stripPrefix(prefix))
  }

  /**
   * Removes one element of "/path/in/url" and return it
   */
  def consumePathElement = {
    var split = path.split("/")
    split.size match {
      case 1 => path
      case more =>

        var elt = split(1)
        stripPathPrefix("/" + elt)
        elt
    }

  }

  def toBytes = {

    //-- Set Content Length if necessary
    if (nextParts.size > 0) {
      addParameter("Content-Length", nextParts.map(_.bytes.length).sum[Int].toString)
    }

    //-- Result
    ByteBuffer.wrap(s"""$operation $path HTTP/$version\r\n${parameters.map(p => s"${p._1}: ${p._2}").mkString("\r\n")}\r\n\r\n${nextParts.map(p => new String(p.bytes)).mkString("", "\r\n", "\r\n\r\n")}""".getBytes("US-ASCII"))
  }

  // Cookies
  //---------------

  /**
   * Catch Cookies
   */
  override def addParameter(name: String, value: String) = {

    if (name == "Cookie") {

      value.trim.split(";").foreach("""([\w]+)=(.+)""".r.findFirstMatchIn(_) match {
        case Some(matched) =>

          var (cookieName, cookieValue) = (matched.group(1) -> matched.group(2))

          logFine(s"Got cookie $cookieName -> $cookieValue")

          cookies = cookies + (cookieName -> cookieValue)

        case None =>

          logFine(s"Cookie but value regexp did not match")
      })

    } else {
      super.addParameter(name, value)
    }
  }

  // Session
  //-------------------
  override def getSession = this.session match {
    case Some(s) => Some(s)
    case None =>
      this.session = Some(Session(this))
      this.session
  }

  override def hasSession = Session.sessionDefined(this)

  // URL Parameters
  //-------------------------

  /**
   *  if not all parameters are met
   */
  def ensureURLParameters(names: List[String]) = {

    names.foreach {
      p =>
        this.getURLParameter(p) match {
          case Some(found) =>
          case None =>
            throw new IllegalArgumentException(s"Required URL Parameter $p was not found ")
        }
    }
  }

  /**
   * Parse URL Parameters from POST form url encoded content
   */
  def parseURLParameters = {
    this.parameters.find(_._1 == "Content-Type") match {
      case Some((_, contentType)) if (contentType.trim.startsWith("application/x-www-form-urlencoded")) =>

        var content = new String(this.bytes)
       
        //println("Parsing URL parameters using base: "+content)

        content.trim().split("&").foreach {
          parameterString =>

            
           // println("P: /"+parameterString+"/")
            val splitValue = parameterString.trim().split("=")
            val name = splitValue(0).trim
            val value = splitValue.length match {
              case 1 => ""
              //-- If a value is set, remove name from array and reconstruct in case the value has unencoded "="
              case other => java.net.URLDecoder.decode(splitValue(1), "UTF-8")
            }
            urlParameters.update(name, value)
        }

      case other =>
    }
  }

  /**
   * If the content type matches application/x-www-form-urlencoded
   * Then try to find in bytes string the whished parameter
   */
  def getURLParameter(name: String): Option[String] = {

    // First try to find in URL Map
    //--------------
    this.urlParameters.get(name) match {
      case Some(value) => Some(value)

      // Look For POST in Content
      //---------------
      case None =>

        this.parameters.find(_._1 == "Content-Type") match {

          // Some URL parameters can be in content -> in bytes
          //-----------------
          case Some((_, contentType)) if (contentType.trim.startsWith("application/x-www-form-urlencoded")) =>

            var content = new String(this.bytes)
           // ("""\b""" + name + """\b""" + """=([\w'"%+_\*\.-]+)(?:&|$$)""").r.findFirstMatchIn(content) match {
            ("""\b""" + name + """\b""" + """=([^&]+)(?:&|$$)""").r.findFirstMatchIn(content) match {

              // Foudn Something, save and return
              case Some(matched) =>
                var decoded = java.net.URLDecoder.decode(matched.group(1), "UTF-8")
                this.urlParameters.update(name, decoded)
                Option(decoded)
              case None =>

                // Look in normal URL parameters
                this.parameters.collectFirst { case param if (param._1 == name) => java.net.URLDecoder.decode(param._2, "UTF-8") }
            }

          // Multi part form data -> explore other parts
          //-----------------
          case Some((_, contentType)) if (contentType.trim.startsWith("multipart/form-data")) =>

            //  println(s"------- Searching part with parameter name")
            this.nextParts.foreach {
              p =>
              /* println(s"----> Part plline: " + p.protocolLines.mkString("\n"))
            println(s"----> Part params: " + p.parameters.mkString("\n"))
            println(s"----> Part content: " + new String(p.bytes))*/

            }
            this.nextParts.collectFirst {

              // Standard text content disposition
              case p if (p.getParameter("Content-Type").isEmpty && p.getParameter("Content-Disposition") != None && p.getParameter("Content-Disposition").get.trim.matches("form-data;\\s*name=\"" + name + "\".*")) =>

                // Found something, save it
                var decoded = java.net.URLDecoder.decode(new String(p.bytes), "UTF-8")
                this.urlParameters.update(name, decoded)
                decoded

              // Binary Content Disposition
              case p if (p.getParameter("Content-Type").isDefined && p.getParameter("Content-Disposition") != None && p.getParameter("Content-Disposition").get.trim.matches("form-data;\\s*name=\"" + name + "\"\\s*;\\s+filename=\".*\".*")) =>

                // Name is File Name
                val filenameExtract = """filename\s*=\s*"([^"]+)"\s*;?""".r
                filenameExtract.findFirstMatchIn(p.getParameter("Content-Disposition").get) match {
                  case Some(m) =>
                    m.group(1)
                  case None =>
                    sys.error("Cannot find filename for name: " + name + " inside content: " + p.getParameter("Content-Disposition").get)

                }

              /*
                val splittedValues = p.getParameter("Content-Disposition").get.trim.split("=")
                splittedValues.zipWithIndex.find {
                  case (s,i) if(s=="filename") => true
                  case other => false
                } match {
                  // Filename is after "filename" index, and remove ";"
                  // Data is within " " boundaries
                  case Some((s,i)) => splittedValues(i+1).drop(1).ex
                  case other => sys.error("Cannot find filename for name: "+name)
                }*/

              //println("FIXME GET NAME FILE NAMES")
              ////this.urlParameters.update(name)
              //""

            }

          // Normal parameters
          case _ => this.parameters.collectFirst { case param if (param._1 == name) => java.net.URLDecoder.decode(param._2, "UTF-8") }

        }

    }

  }

  // Multipart Data Support
  //--------------
  def isMultipart: Boolean = {

    this.parameters.find(_._1 == "Content-Type") match {

      case Some(Tuple2(_, contentType)) if (contentType.trim.matches("multipart/form-data.*")) => true
      case _ => false
    }

  }

  def getMultiPartBoundary: Option[String] = {

    this.parameters.find(_._1 == "Content-Type") match {

      case Some(Tuple2(_, contentType)) if (contentType.matches("multipart/form-data.*")) =>

        """.+; boundary=(.+)\s*""".r.findFirstMatchIn(contentType) match {
          case Some(matched) => Option(matched.group(1))
          case None => None
        }

      case _ => None
    }

  }

  def getPartForFileName(filename: String) = {

    this.nextParts.find {

      // Binary Content Disposition
      case p if (p.getParameter("Content-Type").isDefined && p.getParameter("Content-Disposition") != None && p.getParameter("Content-Disposition").get.trim.matches("form-data;.+name=\"" + filename + "\".*")) =>

        true

      case other => 
          false
    }

  }

  // General Utilities
  //------------------
  def isLocalHost = {
    this.getParameter("Host") match {
      case Some(host) => host.contains("localhost") || host.contains("127.0.0.1")
      case None => false
    }
  }

  def isPOST = operation == "POST"
  def isGET = operation == "GET"
  def isPUT = operation == "PUT"
  def isHEAD = operation == "HEAD"
  def isDELETE = operation == "DELETE"
  def isTRACE = operation == "TRACE"
  def isOPTIONS = operation == "OPTIONS"
  def isCONNECT = operation == "CONNECT"
  def isPATCH = operation == "PATCH"

}

object HTTPRequest extends MessageFactory with TLogSource {

  /**
   * Create a request for an URL
   */
  def GET(urlStr: String): HTTPRequest = {

    var request = prepareRequest(urlStr)

    //-- Set to GET
    request.operation = "GET"

    request

  }

  def POST(urlStr: String): HTTPRequest = {

    //-- Prepare
    var request = prepareRequest(urlStr)

    //-- Set to post
    request.operation = "POST"

    request
  }

  /**
   * Prepare a request based on the URL, with GET action
   */
  def prepareRequest(urlStr: String): HTTPRequest = {

    //-- Split URL
    var url = new URL(urlStr)

    //-- Create message
    //------------------------
    var request = new HTTPRequest("GET", url.getPath(), "1.1")

    //-- Add Host to parameters
    request.addParameter("Host", url.getHost())

    //-- A few Parameters for webbrowsers
    request.addParameter("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:31.0) Gecko/20100101 Firefox/31.0")
    request.addParameter("Accept", """text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8""")
    request.addParameter("Accept-Encoding", "gzip")
    request.addParameter("Accept-Language", "en-US,en;q=0.5")
    request.addParameter("Connection", "keep-alive")

    //-- If some Query parameters, set the content type, length and add part
    url.getQuery() match {
      case null =>
      case "" =>
      case query =>

        //-- Set type
        request.addParameter("Content-Type", "application/x-www-form-urlencoded")

        //-- Create part for this
        var part = new DefaultMimePart
        part += query.getBytes()

        request.append(part)

    }

    //-- Destination URL is a network context parameter
    //-----------------------
    var ctx = Some(new TCPNetworkContext("tcp+http+http://" + url.getHost()))
    request.networkContext = ctx

    request

  }

  def apply(data: Any): HTTPRequest = {

    build(data.asInstanceOf[MimePart])

  }

  var lastFirstMessage: HTTPRequest = null

  /**
   * Create HTTPMessage
   * - 1st line: GET/PUT...  /path/ HTTPVERSION
   */
  def build(part: MimePart): HTTPRequest = {

    // Prepare regexps
    //----------------------
    var firstLineRegexp = """(GET|POST|PUT) (.*) HTTP/([0-9]+\.[0-9]+)""".r
    var parameterLineRegexp = """([\w-])+: (.+)""".r

    // Parse First Line
    //-----------------------
    firstLineRegexp.findFirstMatchIn(part.protocolLines(0)) match {

      //-- Got First Message
      case Some(matched) =>

        logFine(s"[HTTP] -> First Message from part ${part.hashCode} with protocol line: " + part.protocolLines(0))
        lastFirstMessage = new HTTPRequest(matched.group(1), matched.group(2), matched.group(3))

        logFine("Got HTTP Message for path: " + lastFirstMessage.path + " and operation " + lastFirstMessage.operation)

        lastFirstMessage.operation match {
          case "POST" =>
          //println(s"Post message content: ${new String(part.bytes)}");

          case _ =>
        }

        // Add part ot message
        //-------------
        lastFirstMessage(part)

        // Handle Multipart
        //--------------------
         logFine[HTTPMessage]("Multipart: "+lastFirstMessage.isMultipart)
        //println(s"MP: "+lastFirstMessage.isMultipart+"//"+lastFirstMessage.getParameter("Content-Type"))
        if (lastFirstMessage.isMultipart) {

          var boundary = lastFirstMessage.getMultiPartBoundary.get
          var realBoundary = "--" + boundary

          logInfo[HTTPMessage]("Multipart boundary: " + realBoundary)

          //println(s"Split to $boundary")

          // Split boundary, create part and extract parameters
          /*var boundaryBytes = realBoundary.getBytes
          var bytesBuffer = ByteBuffer.wrap(lastFirstMessage.bytes)

          // Search for Boundary
          var continue = true
          var bytes = false
          while (continue) {

            logInfo[HTTPMessage]("Boundary Chunk -> remaining: "+bytesBuffer.remaining()+", boundary size: "+realBoundary.size)
            bytesBuffer.get(boundaryBytes)

            new String(boundaryBytes) match {



                // boundary+"--" is end of stream
              case searched if (searched == realBoundary && bytesBuffer.remaining()==2) =>

                continue = false

              // Found Boundary -> New Part
              case searched if (searched == realBoundary) =>

                logInfo[HTTPMessage]("Found Boundary")

                bytes = false

                var part = new DefaultMimePart
                lastFirstMessage.append(part)

                // Read lines for header part of Part, then move forward on the pure bytes and gather content as bytes
                var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytesBuffer.array(), bytesBuffer.arrayOffset(), bytesBuffer.remaining())))
                var headerBytes = 0
                var headerLines = true
                while (headerLines) {

                  reader.readLine() match {
                    case "" =>

                       logInfo[HTTPMessage]("-- EOF Header")
                      headerLines = false
                      headerBytes += 2
                      bytesBuffer.position(bytesBuffer.position() + headerBytes)
                      bytes = true
                    case other =>

                      logInfo[HTTPMessage]("-- Header -> "+other)
                      part.addParameter(other)
                      headerBytes += (other.getBytes.size + 2)
                  }

                }


              // Content does not match boundary -> add as bytes to part
              case searched =>

                logInfo[HTTPMessage]("Stack bytes")
                lastFirstMessage.nextParts.last += boundaryBytes

            }
          }
          */
          /*var reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(lastFirstMessage.bytes)))
          var continue = true
          while (continue) {

            reader.readLine() match {

              // Boundary -> New Part
              case line if (line.startsWith("--" + boundary)) =>

                var part = new DefaultMimePart
                lastFirstMessage.append(part)

            }
          }*/
          var i = 0;
          var contentBytesPointer = 0
          new String(lastFirstMessage.bytes).split(realBoundary).dropRight(1).drop(1).filter { p => p.trim != "" }.foreach {
            partContent =>

              // Add Boundary to offset
              contentBytesPointer += realBoundary.size + 2

              logFine[HTTPMessage](s"**--> part: $partContent")
              //println(s"**--> part: $lines")

              var part = new DefaultMimePart
              lastFirstMessage.append(part)

              // Add all first lines as parameter, if we find a new line, the remaining goes as content
              var contents = partContent.trim.split("\n")

              
              val parametersContent = contents.toList.takeWhile {
                  l => 
                      //println(s"***-> Line: "+l+" -> "+l.trim()!="")
                      l.trim()!=""
              }
              
              val realContent = contents.toList.dropWhile {
                  l => 
                      l.trim()!=""
              }.drop(1).mkString
              
              
              
              parametersContent.foreach {
                  parameterLine => 
                      logFine[HTTPMessage](s"**Parameter Line: $parameterLine")
                      part.addParameter(parameterLine)
              }
             
              part += realContent.getBytes
              
              /*
               * BACKUP
                
                // First Content part is the header
              contents(0).split("\r\n").foreach {
                pl =>
                  part.addParameter(pl)

                  contentBytesPointer += pl.size + 2

              }
               
               
              // Add Empty separator to bytes pointer
              // contentBytesPointer += 2

              // Add content if any
              if (contents.size > 1) {

                var bytesTotal = contents(1).getBytes.size - 2

                //part += contents(1).getBytes
                var bytesForPart = lastFirstMessage.bytes.slice(contentBytesPointer, contentBytesPointer + bytesTotal)
                part += bytesForPart

                if (bytesTotal <= 40) {
                  logInfo[HTTPMessage]("PartContent: " + new String(bytesForPart))
                  logInfo[HTTPMessage]("PartContent Before: " + contents(1))
                }

                contentBytesPointer += contents(1).getBytes.size
              }

              i += 1*/
          }
        }

      //-- Maybe a Continued Content in case of a multipart message
      case None if (lastFirstMessage != null && lastFirstMessage.isMultipart) =>

        logFine(s"[HTTP] -> Multipart element, create a request with the same path as previous message")
        var message = new HTTPRequest(lastFirstMessage.operation, lastFirstMessage.path, lastFirstMessage.version)
        message.cookies = lastFirstMessage.cookies
        message += part.bytes

        // Add Parameters
        //-------------
        message(part)

        return message

      //-- No Idea
      case _ =>
        logWarn(s"[HTTP] -> Not a first message and not a multipart part")

    }

    return lastFirstMessage

  }

}

class HTTPResponse extends HTTPMessage with MimePart with TLogSource {

  var contentType: String = null
  var content: ByteBuffer = null

  var code = 200

  /**
   * Catch Cookies
   */
  override def addParameter(name: String, value: String) = {

    if (name == "Set-Cookie") {

      value.trim.split(";").foreach("""([\w]+)=(.+)""".r.findFirstMatchIn(_) match {
        case Some(matched) =>

          var (cookieName, cookieValue) = (matched.group(1) -> matched.group(2))

          logFine(s"Got cookie $cookieName -> $cookieValue")

          cookies = cookies + (cookieName -> cookieValue)

        case None =>

          logFine(s"Cookie but value regexp did not match")
      })

    } else {
      super.addParameter(name, value)
    }
  }

  def toBytes: ByteBuffer = {

    var headerLines = List[String]()
    headerLines = headerLines :+ s"HTTP/1.1 ${HTTPCodes.codeToStatus(code)}"

    // Add Standard Parameters
    //-----------------------------------

    //headerLines = headerLines :+ s"Status: ${HTTPErrorCodes.codeToStatus(code)}"

    contentType match {
      case null =>
      case ct => headerLines = headerLines :+ s"Content-Type: $ct"
    }

    //headerLines = headerLines :+ "Cache-Control: no-cache"

    content match {
      case null =>
      case c => headerLines = headerLines :+ s"Content-Length: ${c.remaining}"
    }

    var sessionId = ""
    if (this.getSession.isDefined) {

      //sessionId = s"""Set-Cookie: SSID=${this.getSession.id}; Domain=${this.getSession.host}; Path=${this.getSession.path}; Expires=${this.getSession.validityString};"""
      sessionId = s"""Set-Cookie: SSID=${this.getSession.get.id}; Path=${this.getSession.get.path}; Expires=${this.getSession.get.validityString};"""

      //sessionId = s"""Set-Cookie: SSID=${this.getSession.id}; Expires=${this.getSession.validityString};"""
      //sessionId = s"""Set-Cookie: SSID=${this.getSession.id};"""
      headerLines = headerLines :+ sessionId
    }

    // Add Mime Defined Parameters
    //-------------------------------
    this.parameters.map(v => s"${v._1}: ${v._2}").foreach {
      v => headerLines = headerLines :+ v

    }

    /*var header = s"""HTTP/1.1 $code
Status: 200 OK
Content-Type: $contentType
Cache-Control: no-cache
Content-Length: ${content.capacity}
$sessionId
"""*/
    var header = content match {
      case null => headerLines.mkString("", "\r\n", "\r\n\r\n")
      case _ => headerLines.mkString("", "\r\n", "\r\n\r\n")
    }

    logFine(s"Response Headers: $header //")

    // Create Bytes
    //-------------------
    var totalSize = content match {
      case null => header.getBytes.size
      case _ => header.getBytes.size + content.capacity
    }

    var res = ByteBuffer.allocateDirect(totalSize)
    res.put(header.getBytes)

    content match {
      case null =>
      case c =>
        res.put(c)
        c.clear()
        content = null
    }

    //res.put(ByteBuffer.wrap("\n".getBytes))

    /*if (contentType=="text/html") {
      println(s"Sending: "+new String(res.array))
    }*/
    //println(s"Sending: "+new String(res.array))))))))

    res.flip
    res

  }

  // Content
  //--------------
  def clearResults = {

    this.content = null
  }



  def setTextContent(str: String) = {
    this.contentType = "text/plain"
    this.content = ByteBuffer.wrap(str.getBytes)
  }

}
object HTTPResponse extends MessageFactory with TLogSource {

  def apply(): HTTPResponse = new HTTPResponse
  def apply(data: Any): HTTPResponse = {

    var part = data.asInstanceOf[MimePart]

    //-- Parse First line for result info
    var firstLineRegexp = """(HTTP/1.1) ([0-9]+) (.+)""".r

    var lastFirstMessage: HTTPResponse = null

    firstLineRegexp.findFirstMatchIn(part.protocolLines(0)) match {

      //-- Got First Message
      case Some(matched) =>

        logFine[HTTPResponse](s"[HTTP] -> First Message from part ${part.hashCode} with protocol line: " + part.protocolLines(0))
        lastFirstMessage = new HTTPResponse
        lastFirstMessage.code = matched.group(2).toInt

        //logFine("Got HTTP Message for path: " + lastFirstMessage.path + " and operation " + lastFirstMessage.operation)

        // Add part ot message
        //-------------
        lastFirstMessage(part)

        //-- Decode Data if needed
        logFine[HTTPResponse](s"Bytes available: " + lastFirstMessage.bytes.length)

        lastFirstMessage.parameters.find(_._1 == "Content-Encoding") match {
          case Some((_, "gzip")) =>

            logFine[HTTPResponse]("Bytes are encoded using GZIP, unzip them")

            //-- Create ZIP input stream from bytes
            var zipInput = new GZIPInputStream(new ByteArrayInputStream(lastFirstMessage.bytes))

            logFine[HTTPResponse]("Available: " + zipInput.available())

            try {
              // zipInput.getNextEntry()
              //-- Look for the next entry
              zipInput.available() match {

                //-- Error If nothing to read
                case 0 => throw new RuntimeException("No bytes available in ZIP stream (EOF)")

                //-- Otherwise read in a new array stream
                case 1 =>

                  // Init with twice the size to avoid too much internal array resizing
                  /* var outputStream = new ByteArrayOutputStream(lastFirstMessage.bytes.length*2)

                  // Read loop (read in page size buffers)
                  while(zipInput.available()==1) {
                    zipInput.
                  }

                  lastFirstMessage.bytes = new Array[Byte](size)
                  zipInput.read(lastFirstMessage.bytes)*/

                  // rely on scala for now
                  lastFirstMessage += scala.io.Source.fromInputStream(zipInput, "UTF-8").mkString.getBytes()
              }
            } finally {
              zipInput.close()
            }

          case _ =>
        }
      //lastFirstMessage.bytes

      //-- No Idea
      case _ =>
        logWarn(s"[HTTP] -> Not a first message and not a multipart part")

    }

    return lastFirstMessage

  }

  def apply(contentType: String, content: ByteBuffer): HTTPResponse = {

    var r = new HTTPResponse
    r.contentType = contentType
    r.content = content
    r
  }

  def apply(contentType: String, content: String): HTTPResponse = {

    var r = new HTTPResponse
    r.contentType = contentType
    r.content = ByteBuffer.wrap(content.getBytes)
    r

  }

  //-- Standard codes
  def c503 = {
    var resp = new HTTPResponse
    resp.code = 503
    resp
  }
  def c404 = {
    var resp = new HTTPResponse
    resp.code = 404
    resp
  }
  def c200 = {
    var resp = new HTTPResponse
    resp.code = 404
    resp
  }
  def temporaryRedirect(target: String) = {
    var resp = new HTTPResponse
    resp.code = HTTPCodes.Temporary_Redirect
    resp.addParameter("Location", target)
    resp
  }

}



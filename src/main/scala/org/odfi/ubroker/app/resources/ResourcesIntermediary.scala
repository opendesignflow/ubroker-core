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
package org.odfi.ubroker.app.resources

import org.odfi.ubroker.app.http.message.HTTPIntermediary
import org.odfi.ubroker.app.http.message.HTTPResponse
import java.nio.ByteBuffer
import java.net.URL
import org.odfi.ubroker.app.http.message.HTTPRequest
import java.io.File
import org.odfi.ubroker.app.http.message.HTTPPathIntermediary
import java.nio.channels.Channels
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import org.odfi.tea.files.FileWatcher
import org.odfi.tea.io.TeaIOUtils

class ResourcesIntermediary(basePath: String) extends HTTPPathIntermediary(basePath) {
  name = "Simple File Resources"

  acceptDown[HTTPRequest] {
    message =>

      var r = (message.errors.isEmpty && message.upped == false)
      logFine[ResourcesIntermediary](s"Resource acccepts: " + message.path + " -> " + basePath + " -> " + message.errors.isEmpty)
      r
  }

  //Refuse messages with a path containing WEB-INF
  /*acceptDown { message =>

    !message.path.contains("WEB-INF")
  }*/

  // Resource Search
  //-------------------
  var fileSources = List[String]()

  /**
   * Add a new File Source, that can be used as URL/File Search path
   *
   * IF source is the empty string, it is transformed to "." because it means "current folder"
   */
  def addFilesSource(source: String) = {

    if (source == "") {
      fileSources = "." :: fileSources
    } else {
      fileSources = source :: fileSources
    }

  }
  /**
   * Search a resource at a given path
   */
  def searchResource(path: String): Option[URL] = {

    var extractedPath = path

    // Remove leading/trailing / from base path
    //-------------
    extractedPath = extractedPath.replaceAll("(.*)/$", "$1")
    extractedPath = extractedPath.replaceAll("^/(.*)", "$1")

    // Remove Base Path of application from path (/basePath/whatever must become /whatever)
    //----------
    /*var extractedPath = WebApplication.this.filter.findFirstMatchIn(path) match {
      case Some(extracted) => extracted.group(1)
      case None            => path
    }*/

    //-- Gather file sources
    var allFileSources = this.fileSources ::: ResourcesIntermediary.fileSources

    logFine[ResourcesIntermediary](s"**** Searching resource in : ${fileSources}")

    var res: Option[URL] = None
    // Try class Loader and stanadard file
    logFine[ResourcesIntermediary](s"**** Searching as Resource: ${extractedPath}")
    Thread.currentThread().getContextClassLoader.getResource(extractedPath) match {

      case null =>
        allFileSources.find {
          source =>

            // var possiblePath = new File(s"${source}${extractedPath}").toURI.toURL.toString

            var searchFile = new File(source, extractedPath.replace('/', File.separatorChar))
            logFine[ResourcesIntermediary](s"**** Searching as File: ${searchFile}")
            searchFile match {

              case f if (f.exists) =>

                logFine(s"**** Found!")
                res = Option(f.toURI.toURL)
                true

              case f => false
            }

        }

      case url =>
        logFine[ResourcesIntermediary](s"**** Found!")
        res = Option(url);
        true
    }

    res

  }

  /**
   * Path is the full path, including base application path
   */
  def searchResource(request: HTTPRequest): Option[URL] = {

    // Extract Base Path of application from path
    //----------
    //var extractedPath = WebApplication.this.filter.findFirstMatchIn(s"/${request.qualifier}".replace("//", "/"))
    //var res = this.searchResource(extractedPath.get.group(1))
    var res = this.searchResource(request.path)

    // var res = this.searchResource(request.qualifier)

    //println(s"*** Request Resource search of ${request.qualifier} against: ${WebApplication.this.filter.pattern.toString} : " + extractedPath + ", result -> " + res)

    res

  }

  // Get Message
  //--------------------
  this.onDownMessage {

    message =>

      try {
        //message.path = message.path.stripPrefix(basePath)
        logFine[ResourcesIntermediary](s"Resource process: " + message.path + " -> " + basePath)
        searchResource(message) match {

          //-- Found Read and return
          case Some(resourceURL) =>

            // Handle Bytes Range
            // https://tools.ietf.org/html/rfc7233#page-8
            //---------------
            var (start, stop) = message.getParameter("Range") match {
              case Some(rangeSpec) =>
                println(s"Range: spec $rangeSpec")
                val rangeSpecRegexp = s"""bytes=([0-9]+)(?:-([0-9]+))?""".r
                rangeSpecRegexp.findFirstMatchIn(rangeSpec) match {
                  case Some(res) =>
                    // Check results
                    println(s"Match: ${res.group(1)},${res.group(2)}")
                    (res.group(1), res.group(2)) match {
                      case ("", "") => (-1, -1)
                      case (start, null) => (start.toInt, -1)
                      case (start, stop) => (start.toInt, stop.toInt)
                    }
                  case None =>
                    println(s"No match")
                    (-1, -1)
                }
              case None =>
                (-1, -1)
            }

            //println(s"Range from context ${message.networkContext.toString()}: ($start,$stop)")

            //-- Open Stream
            var resourceStream = resourceURL.openStream
            //var resourceBuffered = new BufferedInputStream(resourceStream)

            //-- Prepare Response
            var response = new HTTPResponse

            //-- Get Data      
            val maxReturnBuffer = 1024 * 1024
            var data = (start, stop) match {

              // Full Content
              case (-1, -1) =>

                // Old
                //response.content = swallow(resourceStream, 0, resourceStream.available())

                // New
                response.content = swallow(resourceURL, 0, resourceStream.available())

              // Remaining
              case (start, -1) =>

                var totalLength = resourceStream.available

                //println(s"Returning Remaninig content: ${resourceStream.available}")

                /*
              resourceStream.available match {
                case size if(size > maxReturnBuffer) => 
                  response.code = 206
                  response.addParameter("Content-Range", s"bytes 0-$maxReturnBuffer/${totalLength}")
                  println(s"-- Starting partial swallow $start -> $maxReturnBuffer")
                  var r = ByteBuffer.wrap(swallow(resourceStream,start,maxReturnBuffer))
                  println(s"-- Finished partial swallow")
                  r
                case size => 
                  ByteBuffer.wrap(org.odfi.tea.io.TeaIOUtils.swallow(resourceStream))
              }*/

                response.code = 206
                response.addParameter("Content-Range", s"bytes $start-${totalLength - 1}/${totalLength}")

                //ByteBuffer.wrap(swallow(resourceStream, start, resourceStream.available()))

                // Old
                //response.content = swallow(resourceStream, start, totalLength)

                // New
                response.content = swallow(resourceURL, start, totalLength)

              // Range
              case (start, stop) =>

                println(s"Returning Range: ($start,$stop)")
                var totalLength = resourceStream.available

                // Return Partial Content
                response.code = 206

                // Give Range in response
                response.addParameter("Content-Range", s"bytes $start-$stop/${totalLength}")

                // resourceStream.skip(start)

                // Old
                //response.content = swallow(resourceStream, start, stop)

                // New
                response.content = swallow(resourceURL, start, stop)
            }

            //resourceBuffered.close
            resourceStream.close

            //response.content = ByteBuffer.wrap(org.odfi.tea.io.TeaIOUtils.swallow(resourceURL.openStream))
            //response.content = data
            // var data = ByteBuffer.wrap(org.odfi.tea.io.TeaIOUtils.swallow(resourceURL.openStream))

            resourceURL.toString match {

              // Standard file contents
              //-----------------------
              case path if (path.endsWith(".html")) =>

                response.contentType = "text/html"
              //response(HTTPResponse("text/html", data), message)
              case path if (path.endsWith(".css")) =>
                response.contentType = "text/css"
              //response(HTTPResponse("text/css", data), message)
              case path if (path.endsWith(".js")) =>
                response.contentType = "application/javascript"
              //response(HTTPResponse("application/javascript", data), message)
              case path if (path.endsWith(".png")) =>
                response.contentType = "image/png"
              // response(HTTPResponse("image/png", data), message)
              case path if (path.endsWith(".jpg")) =>
                response.contentType = "image/jpeg"
              //response(HTTPResponse("image/jpeg", data), message)
              case path if (path.endsWith(".jpeg")) =>
                response.contentType = "image/jpeg"
              // response(HTTPResponse("image/jpeg", data), message)
              case path if (path.endsWith(".gif")) =>
                response.contentType = "image/gif"
              //response(HTTPResponse("image/gif", data), message)
              case path if (path.endsWith(".avi")) =>
                response.contentType = "video/x-msvideo"
              //response(HTTPResponse("video/x-msvideo", data), message)
              case path if (path.endsWith(".eps")) =>
                response.contentType = "application/postscript"
              //response(HTTPResponse("application/postscript", data), message)
              case path if (path.endsWith(".webm")) =>
                response.contentType = "video/webm"
              //response(HTTPResponse("video/webm", data), message)
              case path if (path.endsWith(".mp4")) =>
                response.contentType = "video/mp4"
              case path if (path.endsWith(".pdf")) =>
                response.contentType = "application/pdf"
              //response(HTTPResponse("video/mp4", data), message)

              // Special Views
              //------------------------

              //-- SView with not already created intermediary for this view
              //--  * Create the intermediary
              //case path if (path.endsWith(".sview")) =>
              case _ =>
                response.contentType = "text/plain"
              // response(HTTPResponse("text/plain", data), message)

            }

            // Return response
            //println(s"Finished resource $resourceURL")

            //response.addParameter("Accept-Ranges", "bytes")
            this.response(response, message)

          //-- Nothing found -> Continue to handler
          case None =>

        }
      } catch {
        // TODO Auto-generated catch block
        case e: Throwable =>
          e.printStackTrace();
          throw e
      }
  }

  /**
   * Returns a byte array containing the totality of the InputStream.
   * @param is The available size should return the complete size of the stream
   * @return null if stream is not readable
   */
  def swallow(is: InputStream, start: Int, stop: Int): ByteBuffer = {

    try {

      // Skip start

      // Create Output Byte Buffer
      is.skip(start)
      var expected = stop - start
      var resBuffer = ByteBuffer.allocate(expected)

      //var res = new Array[Byte](stop - start);
      val buffsize = 4096;
      var readBuffer = new Array[Byte](buffsize);
      var sizeRead = 0;
      var position = 0;

      // Swallow
      var continue = true
      while (sizeRead < expected) {

        is.read(readBuffer) match {
          case 0 =>
            continue = false
            sizeRead = expected
          case -1 =>
            continue = false
            sizeRead = expected
          case read =>
            // Copy
            //System.arraycopy(buff, 0, res, position, sizeRead);

            // Write Either whole read size or the remaning content
            if (resBuffer.remaining > read) {
              resBuffer.put(readBuffer, 0, read)
              sizeRead += read;
            } else {
              resBuffer.put(readBuffer, 0, resBuffer.remaining)
              sizeRead = expected
            }

        }

      }

      // Flip to be readable later
      resBuffer.flip()
      //println(s"Returned Buffer with: " + resBuffer.remaining())
      resBuffer

    } catch {
      // TODO Auto-generated catch block
      case e: Throwable =>
        e.printStackTrace();
        throw e
    }

  }

  /**
   * Returns a byte array containing the totality of the InputStream.
   * @param is The available size should return the complete size of the stream
   * @return null if stream is not readable
   */
  def swallow(is: URL, start: Int, stop: Int): ByteBuffer = {

    // Get Cached bytes 
    var bytes = ResourcesIntermediary.mapAndCache(is)

    // Return requested portion

    bytes.position(start)
    bytes.limit(stop)

    // bytes.flip()
    //println(s"Got buffer for ${is} $start -> $stop, available: "+bytes.remaining())

    bytes
    /*
    try {

      // Skip start

      // Create Output Byte Buffer
      is.skip(start)
      var expected = stop - start
      var resBuffer = ByteBuffer.allocate(expected)

      //var res = new Array[Byte](stop - start);
      val buffsize = 4096;
      var readBuffer = new Array[Byte](buffsize);
      var sizeRead = 0;
      var position = 0;

      // Swallow
      var continue = true
      while (sizeRead < expected) {

        is.read(readBuffer) match {
          case 0 =>
            continue = false
            sizeRead = expected
          case -1 =>
            continue = false
            sizeRead = expected
          case read =>
            // Copy
            //System.arraycopy(buff, 0, res, position, sizeRead);

            // Write Either whole read size or the remaning content
            if (resBuffer.remaining > read) {
              resBuffer.put(readBuffer, 0, read)
              sizeRead += read;
            } else {
              resBuffer.put(readBuffer, 0, resBuffer.remaining)
              sizeRead = expected
            }

        }

      }

      // Flip to be readable later
      resBuffer.flip()
      //println(s"Returned Buffer with: " + resBuffer.remaining())
      resBuffer

    } catch {
      // TODO Auto-generated catch block
      case e: Throwable =>
        e.printStackTrace();
        throw e
    }*/

  }
}

object ResourcesIntermediary {

  /*var filesMap = Map(
      ".js" -> "",
      ".png" -> "",
      ".jpeg" -> "",
      ".jpg" -> "",
      ".gif" -> "",
      ".avi" -> "",
      ".css" -> "",
      )*/

  var cacheMap = Map[String, WeakReference[_ <: ByteBuffer]]()

  var cacheWatcher = new FileWatcher
  cacheWatcher.start

  def discardResource(url: URL) = {
    this.synchronized {
      cacheMap.get(url.toExternalForm()) match {
        case Some(ref) => cacheMap = cacheMap - url.toExternalForm()
        case None =>
      }
    }
  }
  def mapAndCache(url: URL, force: Boolean = false): ByteBuffer = {

    var doForce = true
    // Populate Cache
    this.synchronized {

      cacheMap.get(url.toExternalForm()) match {

        case v if (doForce || v.isEmpty || v.get.get == null) =>

          // Map file
          //------------
          url.getProtocol match {
            case "file" =>
              //println("Mapping File")
              var file = new File(url.getFile)

              /*cacheWatcher.onFileChange(file) {
                file.exists() match {
                  case true => 
                    println(s"Discarging cached file: $url")
                    discardResource(url)
                  case false => 
                    println(s"Remapping file: $url")
                mapAndCache(url,force=true)
                }
                
              }*/

              /*var file = new File(url.getFile)
              var fileChannel = FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.READ)

              var bytes = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.getTotalSpace)
              bytes.load()
              var wr = new WeakReference(bytes)*/

              var is = url.openStream()
              var bytes = TeaIOUtils.swallow(is)
              /*var bb = ByteBuffer.allocateDirect(bytes.length)
              bb.put(bytes)
              bb.flip();
              bytes= null*/
              var wr = new WeakReference(ByteBuffer.wrap(bytes))

              cacheMap = cacheMap + (url.toExternalForm() -> wr)
              wr.get.duplicate()
            case other =>

              var is = url.openStream()
              var bytes = TeaIOUtils.swallow(is)
              /*var bb = ByteBuffer.allocateDirect(bytes.length)
              bb.put(bytes)
              bb.flip();
              bytes= null*/

              var wr = new WeakReference(ByteBuffer.wrap(bytes))
              cacheMap = cacheMap + (url.toExternalForm() -> wr)
              wr.get.duplicate()
          }
        case Some(cachedRef) => cachedRef.get.duplicate()
      }

    }

    // REturn values
    //ref.get
    //cacheMap(url.toExternalForm()).get.duplicate()
  }

  var fileSources = List[String]()

  /**
   * Add a new File Source, that can be used as URL/File Search path
   *
   * IF source is the empty string, it is transformed to "." because it means "current folder"
   */
  def addFilesSource(source: String) = {

    var realSource = source match {
      case "" => "."
      case s => s
    }

    fileSources.contains(realSource) match {
      case true =>
      case false => fileSources = source :: fileSources
    }

  }

}

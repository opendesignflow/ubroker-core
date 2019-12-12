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
package org.odfi.ubroker.app.mime

object MimeTypes {
  
  def nameToMime(name:String) : Option[String] = {
    
    name match {

              // Standard file contents
              //-----------------------
              case path if (path.endsWith(".html")) =>

                Some("text/html")
              //response(HTTPResponse("text/html", data), message)
              case path if (path.endsWith(".css")) =>
                Some("text/css")
              //response(HTTPResponse("text/css", data), message)
              case path if (path.endsWith(".js")) =>
                Some("application/javascript")
              //response(HTTPResponse("application/javascript", data), message)
              case path if (path.endsWith(".png")) =>
                Some("image/png")
              // response(HTTPResponse("image/png", data), message)
              case path if (path.endsWith(".jpg")) =>
                Some("image/jpeg")
              //response(HTTPResponse("image/jpeg", data), message)
              case path if (path.endsWith(".jpeg")) =>
                Some("image/jpeg")
              // response(HTTPResponse("image/jpeg", data), message)
              case path if (path.endsWith(".gif")) =>
                Some("image/gif")
              //response(HTTPResponse("image/gif", data), message)
              case path if (path.endsWith(".avi")) =>
                Some("video/x-msvideo")
              //response(HTTPResponse("video/x-msvideo", data), message)
              case path if (path.endsWith(".eps")) =>
                Some("application/postscript")
              //response(HTTPResponse("application/postscript", data), message)
              case path if (path.endsWith(".webm")) =>
                Some("video/webm")
              //response(HTTPResponse("video/webm", data), message)
              case path if (path.endsWith(".mp4")) =>
                Some("video/mp4")
              case path if (path.endsWith(".pdf")) =>
                Some("application/pdf")
              //response(HTTPResponse("video/mp4", data), message)

              // Special Views
              //------------------------

              //-- SView with not already created intermediary for this view
              //--  * Create the intermediary
              //case path if (path.endsWith(".sview")) =>
              case _ =>
                Some("text/plain")
              // response(HTTPResponse("text/plain", data), message)

            }
    
  }
  
}

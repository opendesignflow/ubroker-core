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
package org.odfi.ubroker.app.http.session


import org.odfi.ubroker.app.http.message.HTTPIntermediary
import org.odfi.ubroker.app.http.message.HTTPRequest
import org.odfi.ubroker.app.http.message.HTTPResponse

class SessionIntermediary extends HTTPIntermediary {

  //-- Session
  this.onDownMessage { req =>

    logFine[SessionIntermediary](s"Taking care of session...")
    var s = req.getSession

    logFine[SessionIntermediary](s"Session Number: " + s.get.id)
  }
  this.onUpMessage[HTTPResponse] { resp =>
    
    //println(s"Got Response to answer with " -> resp.relatedMessage)
    
    resp.relatedMessage match {
      case None =>
      case Some(req: HTTPRequest) if (req.session.isDefined) =>
        resp.session = req.getSession
      case _ =>
    }
  }

}

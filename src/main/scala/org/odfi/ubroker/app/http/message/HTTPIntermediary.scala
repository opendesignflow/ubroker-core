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

import org.odfi.ubroker.core.broker.tree.MessageIntermediary

import scala.reflect._

trait HTTPIntermediary extends MessageIntermediary[HTTPRequest] {

  val ttag = classTag[HTTPRequest]
  
  // Operations CLosures
  //----------------------
  def onGET(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isGET) => cl(r)
    case r => 
  }
  def onPOST(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isPOST) => cl(r)
    case r => 
  }
  def onPUT(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isPUT) => cl(r)
    case r => 
  }
  def onHEAD(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isHEAD) => cl(r)
    case r => 
  }
  def onDELETE(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isDELETE) => cl(r)
    case r => 
  }
  def onTRACE(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isTRACE) => cl(r)
    case r => 
  }
  def onOPTIONS(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isOPTIONS) => cl(r)
    case r => 
  }
  def onCONNECT(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isCONNECT) => cl(r)
    case r => 
  }
  def onPATCH(cl: HTTPRequest => Unit) = onDownMessage {
    case r if (r.isPATCH) => cl(r)
    case r => 
  }
  
 

}

class HTTPResponseIntermediary extends MessageIntermediary[HTTPResponse] {

 val ttag = classTag[HTTPResponse]

}

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
package org.odfi.ubroker.core.message.soap

import org.odfi.ubroker.core.broker.tree.MessageIntermediary
import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.ubroker.core.network.NetworkContext
import scala.reflect._



/**
 * Specialised intemerdiary for SOAPHandling
 * 
 * Returns SOAP Faults on errors
 */
trait SOAPIntermediary extends MessageIntermediary[SOAPMessage] {

 
  // Message Type
  //--------------------
  def messageType = classOf[SOAPMessage]
  
  //def messageType = classOf[SOAPMessage]
  
  // Error Handling
  //---------------------
  onError {
    
    (m,e) => 
    
      
      e.printStackTrace()
      
    // Create Fault
    //------------------
    var f = new Fault
    f.reason.text = FaultReasonText()
    f.reason.text.data =  s"${e.getClass().getCanonicalName()}:${e.getLocalizedMessage()}"

    
    // Add To new SOAPMessage
    //-------------
    var resp = new SOAPMessage
    resp.body.content+=f
    resp.qualifier = org.odfi.ubroker.core.message.Message.Qualifier(f)
      
    // Return
    //------------------
    super.response(resp,m)
    
  }
  
  // Response
  //----------------
  def response(payload: ElementBuffer,sourceMessage:SOAPMessage) = {
    
    // Add To new SOAPMessage
    //-------------
    var resp = new SOAPMessage
    resp.body.content+=payload
    
    // Qualifier is extracted from payload
    //--------------
    resp.qualifier = org.odfi.ubroker.core.message.Message.Qualifier(payload)

    
    // Return
    //------------------
    super.response(resp,sourceMessage)
    
  }
  
  // Up with Qualifier
  //----------------
  
  /**
   * Up default SOAPMessage with qualifier taken from org.odfi.ubroker.core.message.Message.Qualifier default extractor
   */
  def up(payload:ElementBuffer,nc: Option[NetworkContext]) : Unit =  {
    up(org.odfi.ubroker.core.message.Message.Qualifier(payload),payload,nc)
  }
  
  /**
   * Up default SOAPMessage with qualifier
   */
  def up(qualifier: String,payload:ElementBuffer,nc: Option[NetworkContext]) : Unit = {
    
    // Create new SOAPMessage
    //-------------
    var resp = new SOAPMessage
    resp.networkContext = nc
    resp.body.content+=payload
    
    resp.qualifier = qualifier
    
    //println("[Upping] "+resp.toXMLString)
    
    // Return
    //------------------
    super.up(resp)
    
  }
  
  
}

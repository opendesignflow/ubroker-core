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
package org.odfi.ubroker.lib.soap


import org.odfi.ubroker.core.message.soap.SOAPMessage
import com.idyria.osi.ooxoo.core.buffers.structural.xelement
import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.ubroker.core.broker.tree.Intermediary
import com.idyria.osi.ooxoo.core.buffers.structural.AnyXList
import com.idyria.osi.ooxoo.core.buffers.datatypes.XSDStringBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.DataUnit
import org.odfi.ubroker.core.message.soap.SOAPIntermediary


/**
 * This intermediary sets or extracts qualifier of a message using Webservice addressing structures
 */
class WSAIntermediary extends SOAPIntermediary {
  
  this.name = "WSA"
  
  // Register XML Elements with AnyContent for SOAP
  //---------------
  AnyXList(classOf[Action])
  
  
  // Message Handler
  //-------------------
  this.onDownMessage {
    
    soapMessage => 
   
      logFine("[WSA] incoming: "+soapMessage.toXMLString)
      
      //-- Look for Action in header
      //---------
      soapMessage.header.content.foreach {
        case action : Action => 
          	
          soapMessage.qualifier = action.toString()
        
          logFine("WSA action: "+action.toString())
        
        case h => 
          
          logFine("WSA header: "+h)
      }
      
  }
  
  this.onUpMessage {
    soap : SOAPMessage =>  
      
      // Add/Update Action in Header from qualifier
      //---------------------------------
      soap.header.content.find {
        
        // Found action, update
        case action : Action => 
          	
         	action.data = soap.qualifier
         	true
        
        case _ =>  false 
          
      } match {
        
        // No action, add
        case None => 
          
           var action = new Action
           action.data = soap.qualifier
           soap.header.content+=action
          
        case _ =>
      }
      
      logFine("[WSA] post wsa: "+soap.toXMLString)
      
  }
 
  
}
 

@xelement(name="Action",ns="http://schemas.xmlsoap.org/ws/2004/08/addressing")
class Action extends XSDStringBuffer {
  
 /* override def streamIn(du: DataUnit) = {
    println("Got DataUnit in Action")
  }*/
  
  
  
}

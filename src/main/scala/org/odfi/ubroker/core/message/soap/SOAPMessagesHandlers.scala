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

import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.AnyXList
import scala.reflect.ClassTag


/**
 * This Intermediary maps received soap messages to closure handlers
 * 
 * This allows a user to use this intermediary to define message handlers at once for multiple messages
 * 
 */
trait SOAPMessagesHandler extends SOAPIntermediary {
  

  /**
   * Maps qualifier strings to actual handling closures
   */
  var messageHandlers = Map[(String,Class[_ <: ElementBuffer]),(  (SOAPMessage,ElementBuffer)  => Any )]()
  
  
  // Intermediary accept only if the qualifier is present in handlers map
  //------------
  this.acceptDown[SOAPMessage]( m => messageHandlers.find( pair => pair._1._1 == m.qualifier)!=None)
  
  //-- Receive Closure
  onDownMessage {
      m => 
        //println("Got Down Message: "+m.toXMLString)
        
        logFine[SOAPMessagesHandler](s"Trying to handle message with qualifier ${m.qualifier} in SOAPMessages")
        
        // Look in handler maps for pairs that are matching this body payload and qualifier
        //------------------
        messageHandlers.foreach {
          
          // Check qualifier
          case ( inputCheck , closure ) if (inputCheck._1 == m.qualifier) => 
            
            logFine[SOAPMessagesHandler](s"Entry matches:  ${inputCheck._1} ")
            
            // Now look for payload
             m.body.content.foreach {
          
               // Payload matches
             	case payload if (payload.getClass == inputCheck._2) =>
             	  
             	  // Call handler, and generate reponse if not already
             	  closure(m,payload.asInstanceOf[ElementBuffer]) match {
             	    case elt : ElementBuffer if(!m.upped) => up(elt, m.networkContext)
             	    case _ =>
             	  }
             	  
             	case _ => 
          
             }
            
            
           case ( inputCheck , closure ) =>
            
            	logFine[SOAPMessagesHandler](s"Entry does not match:  ${inputCheck._1} ")
            
            
        }
       
        
    } 
  
  // Maintenance logic
  //-----------------
  
  /**
   * Removes the closure handler is contained in map
   */
  def removeHandler[T <: ElementBuffer](cl: T => Unit) = {
    
    this.messageHandlers.foreach {
      case (t,handler) if (handler==cl) => this.messageHandlers = this.messageHandlers - t
      case _ => 
    }
    
  }
  
  // React Definition
  //---------------
  
  
  def onMessageType[T<:ElementBuffer](c: Class[T],cl: (SOAPMessage,T) =>Any) = {
    
     // Record in OOXOO
    //-------------------
    AnyXList(c)
    
    // Get Qualifier
    //--------------------
    var qualifier = org.odfi.ubroker.core.message.Message.Qualifier(c)
    
    // Create Closure that makes the checks
    //--------------------
    val realClosure = {
      (m:SOAPMessage , b : ElementBuffer) => 
        
        // If Hte payload type matches the closure one then call
        if (c.isAssignableFrom(b.getClass)) {
          cl(m,b.asInstanceOf[T])
        } 
    }
    
    // Add to map
    //----------------------
    logFine[SOAPMessagesHandler](s"Registering SOAP Message handler: ${(qualifier -> c)} ")
    messageHandlers = messageHandlers + ( (qualifier -> c)  -> realClosure)
    
  }
  
  /**
   * Adds a Response handler with:
   * - Qualifier extracted from element class
   * - Payload must be an instance of the class
   * 
   * @warning This method records the Base Type in AnyXList so make sure the objects will be generated upon reception of SOAP Message
   */
  def on[T <:  ElementBuffer](cl: (SOAPMessage,T) => Any)(implicit tag: ClassTag[T]) = {
    
    // Get Type of input
    //-----------------
    var closureMethod = cl.getClass().getMethods().filter {
      m => m.getName() == "apply" && m.getReturnType() == classOf[Any]
    }.head

    var elementType = tag.runtimeClass.asInstanceOf[Class[T]]
    
    this.onMessageType(elementType,cl)
    
    // Record in OOXOO
    //-------------------
   /* AnyXList(elementType)
    
    // Get Qualifier
    //--------------------
    var qualifier = org.odfi.ubroker.core.message.Message.Qualifier(elementType)
    
    // Create Closure that makes the checks
    //--------------------
    val realClosure = {
      b : ElementBuffer => 
        
        // If Hte payload type matches the closure one then call
        if (elementType.isAssignableFrom(b.getClass)) {
          cl(b.asInstanceOf[T])
        } 
    }
    
    // Add to map
    //----------------------
    println(s"Registering SOAP Message handler: ${(qualifier -> elementType)} ")
    messageHandlers = messageHandlers + ( (qualifier -> elementType)  -> realClosure)*/
    
    
  }
  
}

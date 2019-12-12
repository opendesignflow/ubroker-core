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

import com.idyria.osi.ooxoo.core.buffers.structural.xelement
import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import com.idyria.osi.ooxoo.core.buffers.datatypes.XSDStringBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.XList
import com.idyria.osi.ooxoo.core.buffers.structural.required
import com.idyria.osi.ooxoo.core.buffers.datatypes.IntegerBuffer
import com.idyria.osi.ooxoo.core.buffers.datatypes.BooleanBuffer
import com.idyria.osi.ooxoo.core.buffers.datatypes.DoubleBuffer
import com.idyria.osi.ooxoo.core.buffers.datatypes.DateTimeBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.io.sax.StAXIOBuffer
import java.io.StringReader
import java.util.Calendar



/**
 * This intermediary is a special SOAP handler, that recieves an XML-RPC message format, and tries
 * to find a class method mapping to it.
 * 
 * The search strategy is following:
 * 
 * - Message Qualifier is: the standard extracted from XMLRCP top message
 * - The XMLRCP top message contains the name of the method to look for, along with its arguments
 * 
 * The available methods are parsed and stored in XMLRPC model format when instanciating the class, so that
 * the user can know at anytime what functions this Handler exposes
 * 
 * This base class also provides the facility to set a correct message qualifier depending on the implementing class
 * 
 * Functions return handling:
 * 
 * 	- Any return type beeing an OOXOO Element Buffer will be streamed in SOAP response body as is, and no XMLRPC response format
 *  will be supplied
 * 
 * 
 * Support extend:
 * 
 * struct and array are not supported already, as well as complex return formats
 * 
 * This is only designed to handle very simple method calls, for more complex protocols, the user should switch to SOAP
 * 
 * 
 */
abstract class XMLRPCSoapHandler extends SOAPIntermediary {
  
  
  // Init:
  // - Use introspection to determine the electible methods
  //---------------------
  
  
  
  
  
}

// XML RPC Format
//--------------------
@xelement(name="methodCall")
class MethodCall extends ElementBuffer {
  
  @xelement
  @required
  var methodName : XSDStringBuffer = null
  
  @xelement
  var params  =  new ElementBuffer {
    
    @xelement
    var param = XList {new MethodParameter}
    
  }
  
  
}

object MethodCall {
  
  /**
   * Create from string
   */
  def apply(str:String) : MethodCall = {
    
    var mc = new MethodCall
    mc.appendBuffer(new StAXIOBuffer(new StringReader(str)))
    mc.lastBuffer.streamIn
    mc
    
  }
  
}

@xelement(name="param")
class MethodParameter extends ElementBuffer {
  
  @xelement
  var value : Value = null
  
}

@xelement(name="value")
class Value extends XSDStringBuffer with ElementBuffer {
  
  @xelement(name="i4")
  var i4 : IntegerBuffer = null
  
  @xelement(name="int")
  var int : IntegerBuffer = null
  
  @xelement(name="boolean")
  var boolean : BooleanBuffer = null
  
  @xelement(name="string")
  var string : XSDStringBuffer = null
  
  @xelement(name="double")
  var double : DoubleBuffer = null
  
  @xelement(name="base64")
  var base64 : XSDStringBuffer = null
  
  @xelement(name="dateTime.iso8601")
  var dateTime : DateTimeBuffer = null

  // Utils
  //---------------
  
  /**
   * Returns the class type of this value depending on parsed values
   */
  def toType : Class[_] = {
    
    (i4,int) match {
      case (null,null) => 
      case (_,_) => return classOf[Int]
    }
    
    boolean match {
      case null => 
      case _ => return classOf[Boolean]
    }
    
    double match {
      case null => 
      case _ => return classOf[Double]
    }
    
    base64 match {
      case null => 
      case _ => return classOf[Array[Byte]]
    }
    
    dateTime match {
      case null => classOf[Calendar]
    }
    
    classOf[String]
    
  }
  
  
}

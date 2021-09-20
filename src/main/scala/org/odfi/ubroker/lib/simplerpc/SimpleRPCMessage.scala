package org.odfi.ubroker.lib.simplerpc

import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.io.sax.STAXSyncTrait
import java.nio.ByteBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.xelement
import com.idyria.osi.ooxoo.core.buffers.structural.DataUnit
import com.idyria.osi.ooxoo.core.buffers.structural.xelement_base
import org.odfi.tea.reflect.ReflectUtilities
import com.idyria.osi.ooxoo.core.buffers.structural.xattribute_base
import java.lang.reflect.Modifier
import org.odfi.ubroker.core.message.Message
import org.odfi.ubroker.core.message.MessageFactory
import com.idyria.osi.ooxoo.core.buffers.structural.AnyXList

@xelement
class SimpleRPCMessage extends ElementBuffer with STAXSyncTrait with org.odfi.ubroker.core.message.Message  {
  
  
  Message("simplerpc",SimpleRPCMessage)
  
  def toBytes = ByteBuffer.wrap(this.toXMLString.getBytes)
  
  
  override def streamIn(du:DataUnit) = {
    
    du.attribute match {
      case null => super.streamIn(du)
      case attr => 
        
        // Look for a field
        ReflectUtilities.getField(getClass, attr.name) match {
          case null => 
          case field if (field.getType == classOf[Int]) => 
            ReflectUtilities.setField(this, attr.name, Integer.parseInt(du.value).toInt)
         case field if (field.getType == classOf[String]) =>
           ReflectUtilities.setField(this, attr.name, du.value)
         case field => throw new RuntimeException(s"Receiving message ${getClass.getCanonicalName} failed because field ${field.getName} has a non-supported type: ${field.getType}")
        }
        
    }
    
  }
  
  override def streamOut(du:DataUnit) = {
    
    // Open
    //------------
    var du = new DataUnit()
    du.element = xelement_base(this)
    du.hierarchical = true
    this.getNextBuffer.streamOut(du)
    
    // Output Fields as Attributes
    //-------------
    var allfields = ReflectUtilities.getAllFieldsFromTop(getClass())
    allfields.foreach {
      f => 
        //println(s"Possible Field: ${f.getName} -> ${f.getDeclaringClass.getCanonicalName}")
    }
    allfields.filter {
      f => 
        
        (f.getDeclaringClass != classOf[org.odfi.ubroker.core.message.Message]) &&
        (f.getDeclaringClass != classOf[SimpleRPCMessage]) 
        
    }.foreach {
      f => 
        //println(s"Retained field: ${f.getName}")
        du = new DataUnit()
        du.attribute = new xattribute_base
        du.attribute.name = f.getName
        
        f.setAccessible(true)
        du.value = f.get(this).toString()
        
        this.getNextBuffer.streamOut(du)
        //super.streamOut(du)
    }
    
    // Close
    //-----------------
    du = new DataUnit
    du.setHierarchyClose
    this.getNextBuffer.streamOut(du)
    //super.streamOut(du)
    
  }
  
}
object SimpleRPCMessage extends MessageFactory {
  
  def apply(data: Any): Message = {
    
    println(s"From Protocol: "+data.getClass().getCanonicalName)
    
    var str = new String(data.asInstanceOf[ByteBuffer].array())
    println(s"Message: $str");

/*-
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
    
    // Parse XML
    var any = AnyXList()
    var io = com.idyria.osi.ooxoo.core.buffers.structural.io.sax.StAXIOBuffer(str)
    any.appendBuffer(io)
    io.streamIn
    //any.setNextBuffer(buffer)
    
    println(s"Any Size: ${any.size}")
    any.head.asInstanceOf[SimpleRPCMessage]
    
  }
  
}

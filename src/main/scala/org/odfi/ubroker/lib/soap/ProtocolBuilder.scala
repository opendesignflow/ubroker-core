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

import org.odfi.ooxoo.model.ModelBuilder
import org.odfi.ooxoo.model.Element


/**
 * Provides extra functions to simplify model building
 */
trait ProtocolBuilder extends ModelBuilder {
  
  // Messages Defintions
  //-----------
  implicit class Message(var baseName : String) {
    
  }
  
  var messagesStack = scala.collection.mutable.Stack[Message]()
  
    
    
  def message(baseName : String)(cl : => Any) : Unit = {
    
    messagesStack.push(baseName)
    cl
    messagesStack.pop()
    
    //baseName is cl
    
  }
  
  /**
   * Add Current Base message as Element with "Request" at the end
   */
  def request(cl : => Any) = {
    
    messagesStack.headOption match {
      case Some(message) => 
        
        message.baseName+"Request" is cl
        
      case None => 
    }
    
  } 
  
  /**
   * Add Current Base message as Element with "Response" at the end
   */
  def response(cl : => Any) = {
    
    messagesStack.headOption match {
      case Some(message) => 
        
        message.baseName+"Response" is cl
        
      case None => 
    }
    
  } 
  
  
}

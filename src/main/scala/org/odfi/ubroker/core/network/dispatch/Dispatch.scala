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
package org.odfi.ubroker.core.network.dispatch

import org.odfi.ubroker.core.broker.MessageBroker
import org.odfi.ubroker.core.message.Message
import scala.reflect._

trait Dispatch {
  
  /**
   * Ensures the Provided message is passed to the message broker
   */
  def deliver(m:Message,b:MessageBroker)  : Unit
  
  def lstop : Unit
}


trait TypedDispatch[MT <: Message] extends Dispatch  {
  
  val ttag : ClassTag[MT] 
  
   def deliver(m:Message,b:MessageBroker)  : Unit = {
     //classTag[MT]
     ttag.runtimeClass.isInstance(m) match {
       case true => deliverMessage(m.asInstanceOf[MT],b)
       case false => 
     }
   }
   
   def deliverMessage(m:MT,b:MessageBroker) : Unit
}

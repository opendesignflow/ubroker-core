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
package org.odfi.ubroker.core.message


import java.nio._
import org.odfi.ooxoo.core.buffers.structural.io.sax.STAXSyncTrait
import org.odfi.ooxoo.core.buffers.structural.ElementBuffer

/**
    This class and subtypes are just classes that hold some data without any specific protocol informations

*/
abstract class DataMessage( var bytes : ByteBuffer) extends Message {


    def toBytes : ByteBuffer = bytes

}

class StringDataMessage(var content: String) extends DataMessage(ByteBuffer.wrap(content.getBytes)) {


}

trait XMLDataMessage extends ElementBuffer with STAXSyncTrait with Message {
  
  def toBytes : ByteBuffer = ByteBuffer.wrap(this.toXMLString.getBytes)
  
}

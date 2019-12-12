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
package org.odfi.ubroker.app.http.message

trait HTTPPathIntermediaryTrait extends HTTPIntermediary {
  var basePath : String
}
class HTTPPathIntermediary(var basePath: String) extends HTTPPathIntermediaryTrait {

  //tlogEnableFull[HTTPPathIntermediary]
  
  // Make sure basePath has no double slash
  require(basePath != null)
  basePath = ("/" + basePath).replaceAll("//+", "/")

  acceptDown[HTTPRequest] { message =>
    val res =  message.path.startsWith(basePath)
    logFine[HTTPPathIntermediary](s"($res)Testing "+message.operation+" message with path: " + message.originalPath + ", current dynamic path="+message.path+ ", against " + basePath +" , subtree: "+this.intermediaries.filter{i => i.isInstanceOf[HTTPPathIntermediary]}.map {i => i.asInstanceOf[HTTPPathIntermediary].basePath})
    res
  }

  this.onDownMessage {

    message =>

      // Remove base path, but not the trailing / if any, to make sure
      /*basePath.endsWith("/") match {
        "/" 
      }*/
      message.changePath(message.path.stripPrefix(basePath))

  }

}

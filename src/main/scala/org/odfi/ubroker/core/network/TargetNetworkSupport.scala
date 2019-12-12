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
package org.odfi.ubroker.core.network



/**
 * Trait providing saving facility for classes that would likely send results to multiple network contexes
 */
trait TargetNetworkSupport {
  
  
  var targetNetwork = List[NetworkContext]()
  
  /**
   * Record a network context
   */
  def apply(nc:NetworkContext) = {
    
    // Register
    targetNetwork.synchronized {
    	
      targetNetwork = targetNetwork :+ nc
    }
    
    // Listen on close to avoid invalid sends
    nc.on("close") {
      println("-- Removing from TargetNetwork Support")
      targetNetwork.synchronized {
        targetNetwork = targetNetwork.filterNot(_==nc)
      }
      
    }
  }
  
  
  /**
   * Apply a closure for each configured network contexes
   */
  def onEachTargetNetwork(cl: NetworkContext => Any) : Unit = {
    targetNetwork.synchronized {
    	targetNetwork.foreach(cl(_))
    }
    
  }
  
  
  
}

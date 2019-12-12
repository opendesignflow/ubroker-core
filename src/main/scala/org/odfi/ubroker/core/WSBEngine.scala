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
/**
 *
 */
package org.odfi.ubroker.core

import org.odfi.ubroker.core.network.Network
import org.odfi.ubroker.core.broker.MessageBroker



/**
 * @author rleys
 *
 */
class WSBEngine  extends Lifecycle {


  // Local Aib Bus
  //-------------------------
  //var localBus = new aib

  /**
    Method to send an event to the local aib Bus
  */
  //def ! ( msg :  AnyRef) = this.localBus send msg

  // Layers
  //----------------

  val network = new Network(this)

  val broker = new MessageBroker(this)


  /**
    Init Sub services
  */
  def lInit = {

    Set(network,broker).foreach(_.cycleToInit)

  }

  /**
    Start Sub services
  */
  def lStart = {


   Set(network,broker).foreach(_.cycleToStart)

  }

  /**
    Stop Sub services
  */
  def lStop = {

    Set(network,broker).foreach(_.cycleToStop)
    //this.localBus.doStop
    
    //println("Engine Stopped")
  }

  /**
    Also clean internal aib
  */
  override protected def lClean = {

    //this.localBus.doStop

  }



}
object WSBEngine {



}

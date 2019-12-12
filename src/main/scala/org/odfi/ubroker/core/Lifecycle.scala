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

/**
 * @author rleys
 *
 */
trait Lifecycle {

  private var state = "none"

  // Lifecycle Implementation
  //-----------------------------------

  protected def lInit : Unit

  protected def lStart : Unit

  protected def lSuspend: Unit = {

  }

  protected def lResume: Unit = {

  }

  protected def lStop : Unit

  protected def lClean: Unit = {

  }

  // Lifecycle Management
  //--------------------------

  /**
   * Progess to targetState
   */
  private def cycleTo(targetState: String): Unit = {

    (this.state, targetState) match {

      case ("none", "init") =>

        this.lInit
        this.state = "init"

      // Start
      case (actual, "start") if (actual!="start") =>

        cycleTo("init")
        this.lStart
        this.state = "start"

      // Stop
      case ("start", "stop") =>

        this.lStop
        this.state = "stop"

      case (_, "stop") => throw new RuntimeException("Can only cycle to stop from start")
      
      
      // Ignore
      case _           =>

    }

  }

  def cycleToInit = cycleTo("init")

  def cycleToStart = cycleTo("start")

  def cycleToStop = cycleTo("stop")

}

/*
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
package org.odfi.ubroker.app.http.session

import org.odfi.ubroker.app.http.message._
import java.util.TimeZone
import org.odfi.tea.logging.TLogSource
import scala.reflect.ClassTag

/**
 * Persistent memory accross requests for a user
 */
class Session(var id: String, var host: String) extends TLogSource {

  /**
   * The path must be set to the application base path
   */
  var path = "/"

  /**
   * Validity is always for now 30minutes
   */
  var validity = new java.util.GregorianCalendar(TimeZone.getTimeZone("GMT"))
  validity.add(java.util.Calendar.MINUTE, 30)

  var values = Map[String, Any]()

  /**
   * Add Value to session
   */
  def apply(value: (String, Any)) = {

    logFine[Session](s"[Session] Storing value to id $id and instance ${hashCode}")
    values = values + value

  }

  def apply[T <: Any](name: String): Option[T] = {

    logFine[Session](s"[Session] Searching value from id $id and instance ${hashCode}")
    this.values.get(name).asInstanceOf[Option[T]]
  }

  /**
   * Clear a key in the values
   */
  def clear(name: String) = values.contains(name) match {
    case true =>
      values = values - name; true
    case false => false
  }

  /**
   *
   */
  def validityString: String = {
    //String.format("%tc", this.validity);
    String.format("%1$ta,%1$td-%1$tb-%1$tY %1$tT GMT", this.validity);
  }
  
  
  // Values utils
  //------------
  
  def removeValue(name:String) = synchronized {
    
    this.values.contains(name) match {
      case true => 
        this.values = this.values - name
      case false =>
    }
    
  }
  
  def hasValueOfNameAndType[T](name:String)(implicit tag : ClassTag[T]) =  {
    this.values.get(name) match {
      case Some(v) if (tag.runtimeClass.isInstance(v)) => true
      case other => false
    }
  }
  
  
  def findValueOfType[T](implicit tag : ClassTag[T]) = {
    this.values.find {
      case (k,v:T) => true
      case other => false
    } match {
      case Some((k,v:T)) => Some(k,v)
      case other => None
    }
  }

}

object Session extends TLogSource {

  var sessions = Map[String, Session]()

  var longGenerator = new org.odfi.tea.random.UniqueLongGenerator

  def sessionDefined(message: HTTPRequest): Boolean = {

    message.session.isDefined match {
      case true => true
      case false =>

        // Return true if already in map
        message.cookies.get("SSID") match {
          case Some(ssid) => sessions.contains(ssid)
          case None => false
        }
    }

  }

  /**
   * Creates a new Session, or returns an existing one for the user
   */
  def apply(message: HTTPRequest): Session = {

    // Return if existing
    if (message.session.isDefined)
      return message.session.get

    // Return if already in map
    message.cookies.get("SSID") match {
      case Some(ssid) =>

        logFine[Session](s"[Session] Looking for existing session id $ssid")
        sessions.get(ssid) match {
          case Some(session) =>

            logFine[Session](s"[Session] ... Found")

            return session
          case None =>
        }
      case None =>
    }

    // Try to find Session from HTTP Parameters or create

    // Determine Session Cookie Host
    //---------------
    var host = "localhost"
    (message.parameters.find(_._1 == "X-Forwarded-Host"), message.parameters.find(_._1 == "Host")) match {
      case (Some(forwaredHost), _) => host = forwaredHost._2
      case (None, Some(normalHost)) =>
        //host = normalHost.replaceAll(":[0-9]+", "")
        host = normalHost._2
      case _ =>
    }

    // Try to create an ID
    //----------
    var newSession = new Session(host = host, id = java.lang.Math.abs(longGenerator.generate).toString)

    sessions = sessions + (newSession.id -> newSession)

    logFine[Session](s"[Session] Saving new session id ${newSession.id} for host $host and instance ${newSession.hashCode}")

    newSession
  }
}

/**
 *
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
package org.odfi.ubroker.app.mime

import org.odfi.tea.logging.TLogSource
import scala.collection.mutable.LinkedHashSet

/**
 * Data Type to gatehr data as MimePart
 *
 * a MIME part can contain some parameters like content type or length, and also some pure content
 *
 */
trait MimePart extends TLogSource {

  var protocolLines = List[String]()

  var parameters = Set[(String, String)]()

  val parameterLineRegexp = """([\w-]+): (.+)""".r

  // Merged parts
  //-------------------
  var nextParts = List[MimePart]()

  def append(part: MimePart) = {
    nextParts = part :: nextParts
  }

  // Content
  //-------------
  var contentLength = 0

  private var _bytes = Array[Byte]()

  def bytes = this._bytes

  // Copy MimeParts
  //------------------------
  def apply(part: MimePart) = {

    // Parameters
    part.parameters.foreach {
      case (key, value) => this.addParameter(key, value)
    }
    //this.parameters = this.parameters ++ part.parameters

    // _bytes
    //this.append(part)
    this._bytes = part._bytes

    // Append Next parts
    part.nextParts.foreach {
      p => this.append(p)
    }
  }

  // Parameters
  //---------------------

  /**
   * Record a parameter in internal parameter map
   * Parameters are read from or written as lines like:
   *
   *  NAME: VALUE
   */
  def addParameter(name: String, value: String): Unit = parameters = parameters + (name -> value)

  def addParameter(line: String): Unit = {

    //println(s"Mime Line: $line")

    this.contentLength += line.getBytes.size

    parameterLineRegexp.findFirstMatchIn(line) match {

      case Some(matched) =>

        var (name, value) = (matched.group(1), matched.group(2))

        logFine(s"Param: $name -> $value")

        this.addParameter(name, value)
      case None =>

        logFine(s"Protocol Line: $line")

        protocolLines = line :: protocolLines
    }

  }
  
  def getParameter(sname:String) : Option[String] = parameters.find{ case (name,value) => name ==sname } match {
    case Some((name,value)) => Some(value)
    case None => None
  }

  // Content
  //----------------
  def +=(inputBytes: Array[Byte]): Unit = {
    this._bytes = this._bytes ++ inputBytes
    this.contentLength += inputBytes.size
  }
  
  def asString = new String(this.bytes)

  // Parameters Exploration to find data
  //-------------------------------------------

  /**
   * Search in parameters like this:
   *
   * Content-Disposition: form-data;
   * name="Filedata"; filename="javafx-2_2_25-apidocs.zip"
   */
  def getFileName(): Option[String] = {

    this.parameters.find(_._1 == "Content-Disposition") match {

      case Some(value) =>

        println(s"-> Found Content Disposition: $value")
        """.+; filename="(.+)"\s*""".r.findFirstMatchIn(value._2) match {
          case Some(matched) => Option(matched.group(1))
          case None => None
        }

      case None => None

    }

  }
  
  
}

class DefaultMimePart extends MimePart {

}

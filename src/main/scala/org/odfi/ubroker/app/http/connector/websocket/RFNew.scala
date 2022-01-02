package org.odfi.ubroker.app.http.connector.websocket

import org.odfi.ooxoo.core.buffers.structural.xattribute
import org.odfi.ooxoo.core.buffers.datatypes.LongBuffer
import org.odfi.ooxoo.core.buffers.datatypes.XSDStringBuffer
import org.odfi.ooxoo.core.buffers.structural.xelement
import org.odfi.ooxoo.core.buffers.datatypes.IntegerBuffer
import org.odfi.ooxoo.core.buffers.structural.ElementBuffer
import org.odfi.tea.listeners.ListeningSupport
import org.odfi.ooxoo.core.buffers.structural.XList
import java.net.URL
import org.odfi.ooxoo.core.buffers.structural.io.sax.StAXIOBuffer
import java.io.InputStreamReader
import java.io.File
import java.io.StringReader
import org.odfi.tea.bit.TeaBitUtil

// Common Traits
//-------------------

/**
 * @group rf
 */
trait Named {

  /**
   * name attribute
   * @group rf
   */
  @xattribute
  var name: XSDStringBuffer = null

}

trait NamedAddressed extends Named {

  /**
   * @group rf
   */
  @xattribute(name = "_absoluteAddress")
  var absoluteAddress: LongBuffer = null

  /**
   * @group rf
   */
  @xattribute(name = "_baseAddress")
  var baseAddress: LongBuffer = null

}

/**
 * Top Level element
 *
 */
@xelement(name = "RegisterFile")
class RegisterFile extends Group {

}

/**
 * Companion Objects used as factories
 */
object RegisterFile {

  /**
   * Create a RegisterFile from an URL
   */
  def apply(annotXML: URL): RegisterFile = {

    //  Prepar RF
    var rf = new RegisterFile()

    // Append Input IO
    rf - new StAXIOBuffer(new InputStreamReader(annotXML.openStream))

    // Streamin
    rf.lastBuffer.streamIn

    //rf streamIn
    //rf.getNextBuffer.remove

    // Return
    rf

  }

  /**
   * Create a RegisterFile from a File path
   */
  def apply(file: String): RegisterFile = this.apply(new File(file).toURI.toURL)

  def apply(xml: scala.xml.Elem): RegisterFile = {

    //  Prepar RF
    var rf = new RegisterFile()

    // Append Input IO
    rf - new StAXIOBuffer(new StringReader(xml.toString))

    // Streamin
    rf.lastBuffer.streamIn

    //rf streamIn
    //rf.getNextBuffer.remove

    // Return
    rf

  }
}

/**
 * The <regrooot element
 *
 */
@xelement(name = "Group")
class Group extends ElementBuffer with Named {

  // Attributes
  //-----------------

  // Structure
  //------------------

  //----- regroot
  /**
   * @group rf
   */
  @xelement(name = "Group")
  var groups = XList { new Group }

  //---- Register
  /**
   * @group rf
   */
  @xelement(name = "Register")
  var registers = XList { new Register }

  // General
  //-------------------

  /**
   * @group rf
   */
  def apply(closure: Group => Unit) = {

    closure(this)
  }

  // Search
  //-------------------------

  /**
   * Generic search that can return Register, Ram Entries or Fields
   *
   * Format of search string:
   *
   *  - Register: /path/to/register
   *  - Ram Entry: /path/to/ram[entryIndex]
   *  - Field of ram or register: /path/to/register.field or /path/to/ram[entryIndex].field
   *
   *
   */
  def search(search: String): Any = {

    // Patterns
    val ramEntry = """(.+)\[([0-9]+)]$""".r
    val ramEntryField = """(.+)\[([0-9]+)]\.([\w]+)$""".r
    val regField = """(.+)\.([\w]+)$""".r
    val reg = """(.+)$""".r

    /**
     * match more complex ram search, if not matching, easier matching falls back to Registers
     */
    search.trim match {

      //-- Register Field
      case regField(path, fieldName) =>

        var reg = this.register(path)
        var field = reg.field(fieldName)
        return field

      //-- Register
      case reg(path) =>

        var reg = this.register(path)
        return reg
      case _ => throw new RuntimeException(s"Generic Search expression $search does not match expected format")
    }

  }

  /**
   * Search for a regroot
   *
   * Search String format:
   *
   * xxx/xxx/xxx
   *
   * wherre xxx should be the name of a regroot
   *
   * @group rf
   * @return the found regroot, this if the search string is empty
   * @throws RuntimeException if the specificed search path doesn't point to any regroot
   */
  def group(searchAndApply: String)(implicit closure: Group => Unit): Group = {

    if (searchAndApply == "") {
      return this
    }

    // Split regroot names
    //-----------------
    var regRoots = searchAndApply.split("/")

    // Search
    //----------------
    var currentSearchedRegRoot = this

    regRoots.foreach {
      currentSearchedRegRootName =>

        //-- Look for a regroot with current searched Name in currrent Regroot source
        currentSearchedRegRoot.groups.find(_.name.toString == currentSearchedRegRootName) match {
          case Some(nextRegRoot) => currentSearchedRegRoot = nextRegRoot
          case None =>
            throw new RuntimeException(s"""
                            Searching for regroot ${currentSearchedRegRootName} under ${currentSearchedRegRoot.name} in expression $searchAndApply failed, is the searched path available in the current in use registerfile ?
                        """)
        }

    }

    // Apply Closure if one is provided
    closure(currentSearchedRegRoot)
    //closure(currentSearchedRegRoot)

    // Return
    currentSearchedRegRoot
  }

  /**
   * Search for a Register
   *
   * Search String format:
   *
   * xxxxx/xxx/xxxx/yyyyy
   * path/to/regroot/register
   *
   * With:
   *
   * - xxxx are possible regroots to search
   * - the last path element yyyy beeing the name of the searched register
   *
   * @group rf
   */
  def register(searchAndApply: String)(implicit closure: Register => Unit): Register = {

    // Split String
    //----------------
    var paths = searchAndApply.split("/")
    var searchedRegister = paths.last

    // Regroot: This or the one defined by all the paths elements until the last one
    //---------
    var regRoot = this.group(paths.dropRight(1).mkString("/"))

    // Look For possible register
    //----------------
    regRoot.registers.find(_.name.equals(searchedRegister)) match {
      case Some(searchedRegister) =>

        // Execute closure
        //----------------------
        closure(searchedRegister)

        return searchedRegister
      case None =>
        throw new RuntimeException(s"""
                    Searching for Register ${searchedRegister} under ${regRoot.name} in expression $searchAndApply failed, is the searched path available in the current in use registerfile ?
                """)
    }

  }

  /**
   * Search for a Field
   *
   * Search String format:
   *
   * xxxxx/xxx/xxxx/yyyyy.fieldName
   *
   *
   * With:
   *
   * - xxxx are possible regroots to search
   * - the last path element yyyy beeing the name of the searched register
   * - @fieldName is the name of the field to search on target register
   *
   * @group rf
   */
  def field(searchAndApply: String)(implicit closure: Field => Unit): Field = {

    // Get Register Path and Field Path
    //-------------------------
    var paths = searchAndApply.split("""\.""")

    if (paths.size != 2) {
      throw new IllegalArgumentException(s"Field search format must be: /path/to/register.fieldName, provided: ${searchAndApply}")
    }

    var registerPath = paths.head
    var fieldName = paths.last

    // Search
    //---------------
    this.register(registerPath).field(fieldName)(closure)

  }

}
object Group {

  implicit val defaultRegrootClosure: (Group => Unit) = { t => }

}

/**
 * <Register
 *
 * Search string format:
 *
 * xxxx
 *
 * With:
 * - xxxx beeing the name of a field
 *
 *
 */
@xelement(name = "Register")
class Register extends ElementBuffer with NamedAddressed with ListeningSupport {

  // Attributes
  //-----------

  /**
   * @group rf
   */
  @xattribute(name = "desc")
  var description: XSDStringBuffer = null

  // Structure
  //----------------

  //---- fields

  /**
   * The Builder closure for Fields also calculates the field offset in the register
   *
   * @group rf
   */
  @xelement(name = "Field")
  var fields: XList[Field] = XList {

    //-- Create Field
    //--------------------
    var newField = new Field
    newField.parentRegister = this
    fields.lastOption match {
      case Some(previousField) =>

        newField.offset = previousField.offset + previousField.width

      case None =>
    }
    newField
  }

  // Value
  //------------------

  /**
   * This is the combination of all the subfields reset values
   *
   * @group rf
   */
  def getResetValue: Long = {

    // Base value
    //------------
    var resetValue: Long = 0
    var offset = 0

    // Go through fields and set bits in long
    //--------------
    this.fields.foreach {
      f =>
        //println(s"Updating reg value with field: @${offset} -> ${offset+(f.width-1)} = ${f.reset.data}")

        // Set Bits in result long
        //----------
        resetValue = TeaBitUtil.setBits(resetValue, offset, offset + (f.width - 1), 0)

        // Update offset
        //------------------
        offset = offset + f.width
    }

    resetValue

  }

  /**
   * @group rf
   */
  var valueBuffer = new LongBuffer

  def value = this.valueBuffer

  /**
   *
   * Enables register.value = Long  syntax
   *
   * @group rf
   */
  def value_=(data: Long) = this.valueBuffer.set(data)

  // Search
  //--------------------

  /**
   * Search string format:
   *
   * xxxxx
   *
   * Just the name of the field to search for
   *
   * @group rf
   */
  def field(searchAndApply: String)(implicit closure: Field => Unit): Field = {

    // Look For possible field
    //----------------
    this.fields.find(_.name.equals(searchAndApply)) match {
      case Some(searchedField) =>

        // Execute closure
        //----------------------
        closure(searchedField)

        return searchedField
      case None =>
        throw new RuntimeException(s"""
                    Searching for Field ${searchAndApply} under ${this.name} in expression $searchAndApply failed, is the field defined in the current register ?
                """)
    }

  }

  // Utils
  //------------

  /**
   * Prints To Stdout a string with values of fields
   */
  def explain = {

    println(s"Register $name = 0x${value.toLong.toHexString}")
    this.fields.foreach { f =>
      println(s"-- ${f.name} = 0x${f.value.toHexString}")
    }
  }

  /**
   * Prints To Stdout a string with values of fields
   */
  def explainMemory = {

    println(s"Register $name = 0x${value.data.toLong.toHexString}")
    this.fields.foreach { f =>
      println(s"-- ${f.name} = 0x${f.memoryValue.toHexString}")
    }
  }

}

object Register {

  implicit val defaultClosure: (Register => Unit) = { t => }

}

/**
 * <hwreg
 *
 */
@xelement(name = "Field")
class Field extends ElementBuffer with Named with ListeningSupport {

  // Attributes
  //-----------------

  /**
   * @group rf
   */
  @xattribute
  var width: IntegerBuffer = null

  /**
   * @group rf
   */
  @xattribute(name = "desc")
  var description: XSDStringBuffer = null

  // Value
  //--------------------

  /**
   * Offset of this field inside register.
   * Basically previous field offset + previous field size
   * @group rf
   */
  var offset = 0

  /**
   * parent register type
   * @group rf
   */
  var parentRegister: Register = null

  //var value : Long = 0

  def value: Long = {

    // Read
    var actualValue = parentRegister.value

    // Extract
    TeaBitUtil.extractBits(actualValue, offset, offset + width - 1)
  }

  /**
   * Returns the value of this field based on the actual memory value of the register, with no read
   */
  def memoryValue: Long = {

    // Read
    var actualValue = parentRegister.value

    // Extract
    TeaBitUtil.extractBits(actualValue.data, offset, offset + width - 1)

  }

  /**
   * Set the value of this field:
   *
   * - Read register value
   * - Modify field bits
   * - write register value back
   *
   * @group rf
   */
  def value_=(newData: java.lang.Long) = {

    // Read
    var actualValue: Long = parentRegister.value

    //java.lang.Long.toHexString(node.value)

    //var resultingValue = TeaBitUtil.setBits(actualValue, offset, offset + (width - 1), newData)
    var scalResult = Field.setBits(actualValue, offset, offset + (width - 1), newData)

    // Modify / Write
    this.parentRegister.value = scalResult

    this.@->("value.updated")

    ///println(s"Now val is ${java.lang.Long.toHexString(this.parentRegister.value)}")

  }

}
object Field {

  implicit val defaultClosure: (Field => Unit) = { t => }

  def setBits(baseValue: Long, lsb: Int, msb: Int, newValue: Long): Long = {

    var width = msb - lsb + 1;

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

    // Variables
    //----------------
    //var fullMask: Long = java.lang.Long.decode("0x7FFFFFFFFFFFFFFF");
    var fullMask: Long = 0xFFFFFFFFFFFFFFFFL;
    var fullMaskLeft: Long = 0;
    var newValShifted: Long = 0;
    var resultVal: Long = 0;
    var baseValueRight: Long = 0;

    //-- Shift newVal left to its offset position
    newValShifted = newValue << lsb;

    // println(s"newValShifted: 0x${newValShifted.toHexString} "+newValShifted.toBinaryString)

    //println(s"Base full mask: 0x${fullMask.toHexString} "+fullMask.toBinaryString)

    //-- Suppress right bits of baseValue by & masking with F on the left
    (lsb + width) match {
      case 64 => fullMaskLeft = ~(fullMask << (lsb + width));
      case _  => fullMaskLeft = fullMask << (lsb + width);
    }

    resultVal = baseValue & fullMaskLeft;

    //-- Set Result value in result by ORing with the placed shifted bits new value
    resultVal = resultVal | newValShifted;

    // println(s"Res Temp: 0x${resultVal.toHexString} "+resultVal.toBinaryString)

    // Reconstruct  Right part
    //----------------------------------

    //-- Isolate base value right part
    var fullMaskRight = (64 - lsb) match {
      case 64 => ~(fullMask >>> (64 - lsb))
      case _  => fullMask >>> (64 - lsb)
    }
    //  println(s"Full mask right with lsb: $lsb: ${fullMaskRight.toBinaryString}")
    baseValueRight = baseValue & fullMaskRight;

    //-- Restore right part
    resultVal = resultVal | baseValueRight;

    //println(s"Res: 0x${resultVal.toHexString} "+resultVal.toBinaryString)

    return resultVal

  }

}

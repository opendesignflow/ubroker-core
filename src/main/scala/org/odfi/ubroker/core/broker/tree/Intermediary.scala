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
package org.odfi.ubroker.core.broker.tree

import org.odfi.ubroker.core.message.Message
import scala.util.matching.Regex
import com.idyria.osi.ooxoo.core.buffers.structural.XList
import com.idyria.osi.ooxoo.core.buffers.structural.ElementBuffer
import com.idyria.osi.ooxoo.core.buffers.structural.xattribute
import com.idyria.osi.ooxoo.core.buffers.structural.xelement
import com.idyria.osi.ooxoo.core.buffers.datatypes._
import java.util.regex.Pattern
import org.odfi.tea.logging.TLogSource
import org.odfi.tea.listeners.ListeningSupport
import scala.reflect.ClassTag
import org.xml.sax.helpers.NewInstance
import scala.annotation.tailrec

/**
 * @author rleys
 *
 */
trait Intermediary extends ElementBuffer with TLogSource with ListeningSupport {

  /**
   * A Name for user/api to formally identify the intermediary
   */
  var name: String = ""

  /**
   * Filter is used by Broker to determine if a message should go through this intermediary.
   */
  var filter: Regex = """.*""".r

  var intermediaries: XList[Intermediary] = XList[Intermediary] { new Intermediary {} }

  /**
   * A parent Intermediary if defined, mainly for up operation
   */
  var parentIntermediary: Intermediary = null

  // Up/ Down closures for user processing
  // -- The Closures are stored in a list of closures that all must return true for the message to be accepted
  //---------------

  /**
   * Per default always accept message
   */
  var acceptDownClosures: List[Message => Boolean] = List({ m => true })

  def acceptAllDown = {
    this.acceptDownClosures = List({ m => true })
  }

  /**
   * Define the Closure used to accept a messagein the down direction
   * If the closure does not match the message type, it returns true (ignored)
   */
  def acceptDown[MT <: Message](cl: MT => Boolean)(implicit tag: ClassTag[MT]) = {
    val realCl = {
      (m: Message) =>
        m match {
          case m if (tag.runtimeClass.isInstance(m)) =>
            cl(m.asInstanceOf[MT])
          case other =>
            true
        }

    }
    acceptDownClosures = acceptDownClosures :+ realCl
  }

  var downClosure: (Message => Unit) = null

  /**
   * Per default always accept message
   */
  var acceptUpClosures: List[Message => Boolean] = List({ m => true })

  /**
   * Define the Closure used to accept a messagein the down direction
   *
   */
  def acceptUp(cl: Message => Boolean) = acceptUpClosures = acceptUpClosures :+ cl

  var upClosure: (Message => Unit) = null

  /**
   * Up Actions can start somewhere else that the calling intermediary
   *
   * For example, you can up messages from you top tree instance, and configure it to up from some sub intermediary
   * that add some default message processing, to avoid the user having to know where the start is located
   *
   */
  var upStart: Intermediary = null

  // Up/Down runtime
  //---------------

  /**
   * Run message on the subtree of current intermediary
   */
  def downTree(message: Message): Unit = {
    // Pass to children if closure did not throw anything out
    try {
      this.intermediaries.foreach {
        i =>
          i.down(message)
      }
    } catch {
      case e: Throwable => throw e
    }

  }

  def down(message: Message): Unit = {

    //println(s"[Down] Intermediary with filter: $filter with message: ${message.qualifier}")

    // Ignore message if pattern does not apply
    //--------------
    try {
      filter.findFirstMatchIn(message.qualifier) match {

        //-- Proceed locally and to descendants
        case Some(matchResult) if (acceptDownClosures.forall(_(message))) =>

          logInfo[this.type](s"${depthString("--")}  [Down] Accepted on ${this.name} with filter  $filter and message: ${message.qualifier}@${message.hashCode()} ")

          // Local closure
          //-------------
          message.ignoreCurrentIntermediary match {
            case true =>
              message.ignoreCurrentIntermediary = false;

            case false =>

              downClosure match {
                case null =>
                case closure =>

                  try {

                    closure(message)

                  } catch {
                    case e: ResponseException =>

                    //throw e

                    // In case of error, record to message
                    case e: Throwable =>
                      e.printStackTrace()
                      message(e)

                  } finally {

                  }
              }

          }

          // Pass to children if closure did not throw anything out
          downTree(message)

        //-- Ignore
        case _ =>

          logInfo[this.type](s"${depthString("--")} [Down] Rejected $filter with message: ${message.qualifier}@${message.hashCode()} on ${this.name}")

        //println(s"---> Rejected")

      }

    } catch {
      case e: Throwable =>
        e.printStackTrace()
        message(e)

    } finally {

    }

  }

  final def up(message: Message): Unit = {

    logInfo[Intermediary](s"${depthString("--")} [Up] Upstart is $upStart")
    // if the upStart is delocalized, and upping has not started, start at upStart
    if (this.upStart != null && this.upStart != this && message.upped == false) {

      logInfo[Intermediary](s"${depthString("--")} [Up] Upstart is delocalized")

      this.upStart.up(message)

    } // Otherwise, everything should be normal
    else {

      // Set Upped on message and related if any
      //--------
      message.upped = true
      if (message.relatedMessage.isDefined) {
        message.relatedMessage.get.upped = true
      }

      // Up Closure
      //-------------
      upClosure match {
        case null =>

          logInfo[Intermediary](s"${depthString("--")} [Up] Rejected Intermediary ${name} no Up closure")

        case closure if (acceptUpClosures.forall(_(message)) == false) =>

          logInfo[Intermediary](s"${depthString("--")} [Up] Rejected Intermediary ${name} with filter: $filter with message: ${message.qualifier}")

        case closure =>

          logInfo[Intermediary](s"${depthString("--")} [Up] Accepted Intermediary ${name} with filter: $filter with message: ${message.qualifier}")

          closure(message)
      }

      // Pass to parent if possible
      //---------------
      if (this.parentIntermediary != null) {

        this.parentIntermediary.up(message)

      }

    }

    //println(s"[Up] Intermediary with filter: $filter with message: ${message.qualifier}")

  }

  // Response
  //----------------------------

  /**
   * Sends up a message as response to another one
   */
  def response(responseMessage: Message, sourceMessage: Message): Unit = {

    //throw new ResponseException(responseMessage)
    // Copy context
    responseMessage.networkContext = sourceMessage.networkContext

    // Set related message
    responseMessage.relatedMessage = Some(sourceMessage)

    // Up :)
    up(responseMessage)

  }
  /*def response(responseMessage: Message): Unit = {

    //throw new ResponseException(responseMessage)

  }*/

  // Language
  //-------------------

  def detach = this.parentIntermediary match {
    case null =>
    case other => other.-=(this)
  }

  /**
   * Add an intermediary to this current intermediary
   *
   * @return The added intermediary for nicer api usage
   */
  def <=[I <: Intermediary](intermediary: I): I = {

    intermediaries.contains(intermediary) match {
      case true =>
      case false =>
        intermediaries += intermediary
        intermediary.parentIntermediary = this
        intermediary.@->("parent.new")
    }
    intermediary

  }

  def onNewParentIntermediary(cl: => Any) = {
    this.on("parent.new") {
      cl
    }
  }

  /**
   * Add an intermediary to this current intermediary as first child
   *
   * @return The added intermediary for nicer api usage
   */
  def prepend(intermediary: Intermediary): Intermediary = {

    intermediary.detach

    var newIn = XList[Intermediary] { new Intermediary {} }
    newIn += intermediary
    intermediary.parentIntermediary = this
    intermediaries.foreach(newIn += _)
    intermediaries = newIn

    intermediary
  }

  def addIntermediaryBefore(targetIntermediary: Intermediary, newIntermediary: Intermediary): Intermediary = {

    intermediaries.indexOf(targetIntermediary) match {
      case -1 => throw new IllegalArgumentException(s"Cannot add intermediary $newIntermediary before $targetIntermediary, because $targetIntermediary is not part of this subtree")
      case i =>
        intermediaries.insert(i, newIntermediary.asInstanceOf[Intermediary])
        newIntermediary.asInstanceOf[Intermediary].parentIntermediary = this
        newIntermediary.@->("parent.new")
        triggerIntermediaryAdded(newIntermediary)
    }
    newIntermediary
  }
  def addIntermediaryAfter(targetIntermediary: Intermediary, newIntermediary: Intermediary): Intermediary = {

    intermediaries.indexOf(targetIntermediary) match {
      case -1 => throw new IllegalArgumentException(s"Cannot add intermediary $newIntermediary before $targetIntermediary, because $targetIntermediary is not part of this subtree")
      case i =>
        intermediaries.insert(i + 1, newIntermediary)
        newIntermediary.asInstanceOf[Intermediary].parentIntermediary = this
        newIntermediary.@->("parent.new")
        triggerIntermediaryAdded(newIntermediary)
    }
    newIntermediary
  }

  def triggerIntermediaryAdded(targetIntermediary: Intermediary) = {
    this.@->("intermediary.add", targetIntermediary)
  }

  def onIntermediaryAdded[T <: Intermediary: ClassTag](cl: T => Any) = {
    this.onWith[T]("intermediary.add") {
      (t: T) =>
        cl(t)
    }
  }

  /**
   * Remove an intermediary to this current intermediary
   *
   * @return The removed intermediary for nicer api usage
   */
  def -=(intermediary: Intermediary): Intermediary = {

    var newIn = XList[Intermediary] { new Intermediary {} }
    intermediaries.filterNot { _ == intermediary }.foreach(newIn += _)
    intermediaries = newIn

    intermediary.parentIntermediary = null

    intermediary
  }

  // Utilities
  //-----------------

  /**
   * Returns the depth of this intermediary
   */
  def depth: Int = {

    var current = this
    var count = 0
    var steps = while (current.parentIntermediary != null) {
      current = current.parentIntermediary;

      count += 1
    }

    count

  }

  /**
   * Returns a string formed by the separated repeated $depth times
   */
  def depthString(separator: String): String = (for (i <- 0 to depth) yield separator).mkString

  /**
   * Search first parent matching the provided function
   */
  def findParent(cl: Intermediary => Boolean): Option[Intermediary] = {

    var current = this.parentIntermediary
    var res: Option[Intermediary] = None
    while (current != null && res == None) {

      //-- Try to match
      cl(current) match {
        case true => res = Some(current)
        case false =>
      }

      //-- Update current
      current = current.parentIntermediary;

    }

    res

  }

  def findParentOfType[T <: Intermediary](implicit tag: ClassTag[T]): Option[T] = {

    this.findParent { p =>

      // println(s"Parent type search testing: ${p.getClass.getCanonicalName} against ${}")
      tag.runtimeClass.isAssignableFrom(p.getClass)
    }.asInstanceOf[Option[T]]

  }

  def findTopMostIntermediaryOfType[IT <: Intermediary](implicit tag: ClassTag[IT]): Option[IT] = {

    var currentParent = this.parentIntermediary
    var lastFound: Option[IT] = None
    while (currentParent != null) {

      tag.runtimeClass.isInstance(currentParent) match {
        case true =>
          lastFound = Some(currentParent.asInstanceOf[IT])
          currentParent = currentParent.parentIntermediary
        case false =>
          currentParent = currentParent.parentIntermediary
      }

    }

    lastFound

  }

  def findTopMostIntermediary = {

    var currentParent = this.parentIntermediary
    var lastFound = this
    while (currentParent != null) {

      lastFound = currentParent
      currentParent = lastFound.parentIntermediary

    }

    lastFound

  }

  /**
   * Maps parent intermediaries using provided closure
   * Result list is reversed so the first element is the top parent
   */
  def mapParentIntermediaries[B](cl: Intermediary => B) = {

    var current = this.parentIntermediary
    var res = List[B]()
    while (current != null) {

      //-- Try to match
      res = res :+ cl(current)

      //-- Update current
      current = current.parentIntermediary;

    }

    res.reverse

  }

  /**
   * Find child is depth first
   */
  def findChildOfType[T <: Intermediary](implicit tag: ClassTag[T]): Option[T] = {
    findChild(i => tag.runtimeClass.isInstance(i)) match {
      case Some(r) => Some(r.asInstanceOf[T])
      case None => None
    }
  }
  /**
   * This search is recursive
   */
  def findChild(cl: Intermediary => Boolean): Option[Intermediary] = {

    var childrenToProcess = scala.collection.mutable.Stack[Intermediary]()
    childrenToProcess ++= this.intermediaries
    var res: Option[Intermediary] = None
    while (res.isEmpty && childrenToProcess.isEmpty == false) {

      var current = childrenToProcess.pop()
      cl(current) match {
        case true => res = Some(current)
        case false => childrenToProcess ++= current.intermediaries
      }

    }

    /*var res : Option[Intermediary] = None
    this.intermediaries.foreach {
      case i if (cl(i)==true) => res = Some(i)
      case other => other.findChild(cl)
    }*/

    res

  }

}

class ResponseException(var responseMessage: Message) extends Exception {

}

object Intermediary {

  object Filter {

    /**
     * Returns a filter regexp for the provided string, like this:
     *
     * .*$str.*  with the str content beeing escaped
     *
     */
    def apply(str: String): Regex = {

      s""".*${Pattern.quote(str)}.*""".r

    }

  }

}

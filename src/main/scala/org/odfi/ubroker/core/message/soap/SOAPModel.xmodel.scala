/*
 * #%L
 * WSB Core
 * %%
 * Copyright (C) 2008 - 2017 OpenDesignFlow.org
 * %%
 * This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.odfi.ubroker.core.message.soap

import com.idyria.osi.ooxoo.model.out.scala._
import com.idyria.osi.ooxoo.model.producers
import com.idyria.osi.ooxoo.model.producer
import com.idyria.osi.ooxoo.model.ModelBuilder
import com.idyria.osi.ooxoo.core.buffers.datatypes.CDataBuffer
import com.idyria.osi.ooxoo.model.Element

@producers(Array(
  new producer(value = classOf[ScalaProducer])
))
class SOAP extends ModelBuilder {

  name = "SOAPModel"

  namespace("env" -> "http://www.w3.org/2003/05/soap-envelope")
  parameter("scalaProducer.targetPackage" -> "org.odfi.ubroker.core.message.soap")


  "env:Envelope" is {


    "env:Header" is {

      any

    }

    "env:Body" is {

      any

    }

  }

  "env:Fault" is {

    "env:Code" is {

      "env:Subcode" is {
        "env:Value" ofType "string"
      }

      "env:Value" valueEnum("VersionMismatch", "MustUnderstand", "DataEncodingUnknown", "Sender", "Receiver")

      /*var subCode = "env:Subcode" is {

      }
      subCode is {

          "env:Value" ofType "string"

          importElement(subCode)
          //"env:Subcode" is {
          //}
      }
      subCode*/
      /*var subCode : Element = "env:Subcode" is {

          "env:Value" ofType "string"

          importElement(subCode)
          //"env:Subcode" is {
          //}
      }*/
    }

    "env:Reason" is {
      //"env:Text" ofType "string"
      "env:Text" is {
        ofType("cdata")

      }

      "env:Reason" is {}
    }

    // Optional
    //------------
    "env:Node" ofType "uri"
    "env:Role" ofType "uri"

    "env:Detail" is {

      any

    }


  }

}

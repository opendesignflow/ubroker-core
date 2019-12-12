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
package org.odfi.ubroker.app.http.message

object HTTPCodes {

  val Continue = 100
  val Switching_Protocols = 101
  val Processing = 102
  val OK = 200
  val Created = 201
  val Accepted = 202
  val Non_Authoritative_Information = 203
  val No_Content = 204
  val Reset_Content = 205
  val Partial_Content = 206
  val Multi_Status = 207
  val Already_Reported = 208
  val IM_Used = 226
  val Multiple_Choices = 300
  val Moved_Permanently = 301
  val Found = 302
  val See_Other = 303
  val Not_Modified = 304
  val Use_Proxy = 305
  val Reserved = 306
  val Temporary_Redirect = 307
  val Permanent_Redirect = 308
  val Bad_Request = 400
  val Unauthorized = 401
  val Payment_Required = 402
  val Forbidden = 403
  val Not_Found = 404
  val Method_Not_Allowed = 405
  val Not_Acceptable = 406
  val Proxy_Authentication_Required = 407
  val Request_Timeout = 408
  val Conflict = 409
  val Gone = 410
  val Length_Required = 411
  val Precondition_Failed = 412
  val Request_Entity_Too_Large = 413
  val Request_URI_Too_Long = 414
  val Unsupported_Media_Type = 415
  val Requested_Range_Not_Satisfiable = 416
  val Expectation_Failed = 417
  val Unprocessable_Entity = 422
  val Locked = 423
  val Failed_Dependency = 424
  val Upgrade_Required = 426
  val Precondition_Required = 428
  val Too_Many_Requests = 429
  val Request_Header_Fields_Too_Large = 431
  val Internal_Server_Error = 500
  val Not_Implemented = 501
  val Bad_Gateway = 502
  val Service_Unavailable = 503
  val Gateway_Timeout = 504
  val HTTP_Version_Not_Supported = 505
  val Variant_Also_Negotiates_Experimental = 506
  val Insufficient_Storage = 507
  val Loop_Detected = 508
  val Not_Extended = 510
  val Network_Authentication_Required = 511

  val codes = Map(
    (100 -> "Continue"),
    (101 -> "Switching Protocols"),
    (102 -> "Processing"),
    (200 -> "OK"),
    (201 -> "Created"),
    (202 -> "Accepted"),
    (203 -> "Non-Authoritative Information"),
    (204 -> "No Content"),
    (205 -> "Reset Content"),
    (206 -> "Partial Content"),
    (207 -> "Multi-Status"),
    (208 -> "Already Reported"),
    (226 -> "IM Used"),
    (300 -> "Multiple Choices"),
    (301 -> "Moved Permanently"),
    (302 -> "Found"),
    (303 -> "See Other"),
    (304 -> "Not Modified"),
    (305 -> "Use Proxy"),
    (306 -> "Reserved"),
    (307 -> "Temporary Redirect"),
    (308 -> "Permanent Redirect"),
    (400 -> "Bad Request"),
    (401 -> "Unauthorized"),
    (402 -> "Payment Required"),
    (403 -> "Forbidden"),
    (404 -> "Not Found"),
    (405 -> "Method Not Allowed"),
    (406 -> "Not Acceptable"),
    (407 -> "Proxy Authentication Required"),
    (408 -> "Request Timeout"),
    (409 -> "Conflict"),
    (410 -> "Gone"),
    (411 -> "Length Required"),
    (412 -> "Precondition Failed"),
    (413 -> "Request Entity Too Large"),
    (414 -> "Request-URI Too Long"),
    (415 -> "Unsupported Media Type"),
    (416 -> "Requested Range Not Satisfiable"),
    (417 -> "Expectation Failed"),
    (422 -> "Unprocessable Entity"),
    (423 -> "Locked"),
    (424 -> "Failed Dependency"),
    (426 -> "Upgrade Required"),
    (428 -> "Precondition Required"),
    (429 -> "Too Many Requests"),
    (431 -> "Request Header Fields Too Large"),
    (500 -> "Internal Server Error"),
    (501 -> "Not Implemented"),
    (502 -> "Bad Gateway"),
    (503 -> "Service Unavailable"),
    (504 -> "Gateway Timeout"),
    (505 -> "HTTP Version Not Supported"),
    (506 -> "Variant Also Negotiates (Experimental)"),
    (507 -> "Insufficient Storage"),
    (508 -> "Loop Detected"),
    (510 -> "Not Extended"),
    (511 -> "Network Authentication Required"))

  def codeToStatus(code: Integer): String = {

    codes.get(code) match {
      case None => throw new RuntimeException(s"HTTP Code $code does not exist or is Unassigned ")
      case Some(text) => s"$code $text"
    }
  }

}


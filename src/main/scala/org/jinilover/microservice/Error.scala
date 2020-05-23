package org.jinilover.microservice

import scala.util.control.ControlThrowable

sealed abstract class Error extends Exception with ControlThrowable {

}

case class InputError(msg: String) extends Error
case class ServerError(msg: String) extends Error

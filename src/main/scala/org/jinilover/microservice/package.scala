package org.jinilover

package object microservice {
  import scala.util.control.ControlThrowable

  sealed abstract class Error extends Exception with ControlThrowable

  case class InputError(msg: String) extends Error
  case class ThrowableError(override val getCause: Throwable) extends Error

  trait Log[F[_]] {
    def error(err: Error): F[Unit]
    def warn(msg: String): F[Unit]
    def info(msg: String): F[Unit]
    def debug(msg: String): F[Unit]
  }

//  object Log {
//    def default
//  }
}

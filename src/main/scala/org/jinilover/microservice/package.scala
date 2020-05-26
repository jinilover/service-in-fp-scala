package org.jinilover

import cats.effect.IO
import org.slf4j.LoggerFactory

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

  object Log {
    def default:IO[Log[IO]] = IO(new Slf4jLog)

    class Slf4jLog extends Log[IO] {
      private val logger = LoggerFactory.getLogger("org.jinilover.microservice")

      override def error(err: Error): IO[Unit] =
        err match {
          case InputError(msg) => IO(logger.warn(msg))
          case ThrowableError(cause) => IO(logger.error("", cause))
        }

      override def warn(msg: String): IO[Unit] = IO(logger.warn(msg))

      override def info(msg: String): IO[Unit] = IO(logger.info(msg))

      override def debug(msg: String): IO[Unit] = IO(logger.debug(msg))
    }

  }
}

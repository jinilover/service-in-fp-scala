package org.jinilover.microservice.web

import cats.effect.{ContextShift, ExitCode, IO, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

trait WebServer[F[_]] {
  def start: Stream[F, ExitCode]
}

object WebServer {
  def default(
       routes: Routes[IO])(
       implicit ec: ExecutionContext,
       timer: Timer[IO],
       F: ContextShift[IO]) : WebServer[IO] =
    new Http4sServer(routes)

  class Http4sServer(
          routes: Routes[IO])(
          implicit ec: ExecutionContext,
          timer: Timer[IO],
          F: ContextShift[IO]) // TODO pass in config for the host/port
    extends WebServer[IO] {

    override def start: Stream[IO, ExitCode] =
      BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0") // TODO config
        .withHttpApp(routes.routes)
        .serve
  }
}

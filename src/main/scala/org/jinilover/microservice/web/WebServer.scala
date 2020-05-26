package org.jinilover.microservice.web

import cats.effect.{ContextShift, ExitCode, IO, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.jinilover.microservice.ConfigTypes.WebServerConfig

import scala.concurrent.ExecutionContext

trait WebServer[F[_]] {
  def start: Stream[F, ExitCode]
}

object WebServer {
  def default(
       routes: Routes[IO]
     , webConfig: WebServerConfig)(
       implicit ec: ExecutionContext
     , timer: Timer[IO]
     , F: ContextShift[IO]) : WebServer[IO] =
    new Http4sServer(routes, webConfig)

  class Http4sServer(
          routes: Routes[IO]
        , webConfig: WebServerConfig)(
          implicit ec: ExecutionContext
        , timer: Timer[IO]
        , F: ContextShift[IO])
    extends WebServer[IO] {

    override def start: Stream[IO, ExitCode] =
      BlazeServerBuilder[IO]
        .bindHttp(webConfig.port, webConfig.host)
        .withHttpApp(routes.routes)
        .serve
  }
}

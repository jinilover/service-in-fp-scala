package org.jinilover
package microservice
package web

import scala.concurrent.ExecutionContext

import cats.effect.{ContextShift, ExitCode, IO, Timer}

import fs2.Stream

import org.http4s.server.blaze.BlazeServerBuilder

import ConfigTypes.WebServerConfig

trait WebServer[F[_]] {
  def start: Stream[F, ExitCode]
}

object WebServer {
  def default(
       routes: WebApi[IO]
     , webConfig: WebServerConfig)(
       implicit ec: ExecutionContext
     , timer: Timer[IO]
     , F: ContextShift[IO]) : WebServer[IO] =
    new Http4sServer(routes, webConfig)

  class Http4sServer(
          routes: WebApi[IO]
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

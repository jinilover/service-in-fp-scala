package org.jinilover.microservice

import java.util.concurrent.Executors

import cats.effect.{ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._

import org.jinilover.microservice.db.Migrations
import org.jinilover.microservice.link.LinkService
import org.jinilover.microservice.web.{Routes, WebServer}
import org.jinilover.microservice.ops.OpsService

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    bootstrap()

  def bootstrap(): IO[ExitCode] = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

    for {
      _ <- Migrations.default().migrate
      opsService = OpsService.default
      linkService = LinkService.default[IO]
      routes = Routes.default[IO](opsService, linkService)
      exitCode <- WebServer.default(routes).start.compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}

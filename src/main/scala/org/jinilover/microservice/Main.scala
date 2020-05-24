package org.jinilover.microservice

import java.time.Clock
import java.util.concurrent.Executors

import cats.effect.{ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._

import org.jinilover.microservice.persistence.{Doobie, LinkPersistence, Migrations}
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
      clock = Clock.systemDefaultZone()
      xa = Doobie.transactor
      persistence = LinkPersistence.default(xa, clock)
      linkService = LinkService.default[IO](persistence)
      routes = Routes.default[IO](opsService, linkService)
      exitCode <- WebServer.default(routes).start.compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}

package org.jinilover.microservice

import java.util.concurrent.Executors

import cats.effect.{ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    bootstrap()

  def bootstrap(): IO[ExitCode] = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

    for {
      _ <- Migrations.default().migrate

      routes = Routes.default[IO] ()
      exitCode <- WebServer.default(routes).start.compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}

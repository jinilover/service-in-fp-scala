package org.jinilover.microservice

import cats.effect.IO
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException

trait Migrations[F[_]] {
  def migrate: F[Unit]
}

object Migrations {
  def default(): Migrations[IO] =
    new FlywayMigrations()

  private class FlywayMigrations() extends Migrations[IO] {
    override def migrate: IO[Unit] =
      IO {
        val flyway = new Flyway()
        //TODO use config
        flyway.setDataSource("jdbc:postgresql://localhost:5432/postgres", "postgres", "password")
        flyway.migrate()
      }.handleErrorWith {
        case fwe: FlywayException => IO.raiseError(fwe)
      }.flatMap { i =>
        IO{println(s"$i migrations performed")}
      }
  }
}

package org.jinilover
package microservice
package persistence

import cats.effect.IO
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.jinilover.microservice.ConfigTypes.DbConfig

trait Migrations[F[_]] {
  def migrate: F[Unit]
}

object Migrations {
  def default(log: Log[IO], dbConfig: DbConfig): Migrations[IO] =
    new FlywayMigrations(log, dbConfig)

  private class FlywayMigrations(log: Log[IO], dbConfig: DbConfig) extends Migrations[IO] {
    override def migrate: IO[Unit] =
      IO {
        val flyway = new Flyway()
        flyway.setDataSource(dbConfig.url, dbConfig.user, dbConfig.password)
        flyway.migrate()
      }.handleErrorWith {
        case fwe: FlywayException => IO.raiseError(fwe)
      }.flatMap { i =>
        log.info(s"$i migrations performed")
      }
  }
}

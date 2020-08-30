package org.jinilover
package microservice
package config

import cats.effect.IO

import pureconfig._
import pureconfig.generic.auto._

import ConfigTypes.AppConfig

trait ConfigLoader[F[_]] {
  def load: F[AppConfig]
}

object ConfigLoader {
  def default: ConfigLoader[IO] = new PureconfigLoader

  class PureconfigLoader extends ConfigLoader[IO] {
    override def load: IO[AppConfig] =
      IO {
        ConfigSource.default.at("org.jinilover.microservice").loadOrThrow[AppConfig]
      }.redeemWith(e => IO.raiseError(new ConfigError(e.getMessage)), a => IO(a))
  }
}

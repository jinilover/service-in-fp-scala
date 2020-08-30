package org.jinilover.microservice.config

import org.jinilover.microservice.ConfigTypes.{ AppConfig, DbConfig, WebServerConfig }
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure

class ConfigLoaderSpec extends Specification {
  override def is: SpecStructure =
    s2"""
        ConfigLoader
          should load the required AppConfig $loadAppConfig
      """

  def loadAppConfig = {
    val expected = AppConfig(
      DbConfig("jdbc:postgresql://localhost:5432/postgres", "postgres", "password"),
      WebServerConfig("0.0.0.0", 8080)
    )

    ConfigLoader.default.load.unsafeRunSync() must be_==(expected)
  }
}

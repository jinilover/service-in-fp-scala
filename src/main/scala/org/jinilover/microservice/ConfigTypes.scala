package org.jinilover
package microservice

object ConfigTypes {
  case class AppConfig(db: DbConfig
                     , webserver: WebServerConfig
                     )

  case class DbConfig(url: String
                    , user: String
                    , password: String
                    )

  case class WebServerConfig(host: String
                           , port: Int
                           )
}

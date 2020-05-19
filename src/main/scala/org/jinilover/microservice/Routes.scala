package org.jinilover.microservice

import cats.effect.Sync
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._

trait Routes[F[_]] {
  def routes: HttpApp[F]
}

object Routes {
  def default[F[_]: Sync](): Routes[F] =
    new Http4sRoutes[F]()

  class Http4sRoutes[F[_]: Sync]
    extends Routes[F]
    with Http4sDsl[F] {

    def opsRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
      case GET -> Root  => Ok("Welcome to REST servce in functional Scala!")
    }

    override def routes: HttpApp[F] =
      opsRoutes.orNotFound
  }

}

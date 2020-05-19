package org.jinilover.microservice

import cats.effect.Sync

import io.circe.Encoder

import org.http4s.{EntityEncoder, HttpApp, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.circe._

import org.jinilover.microservice.OpsTypes.VersionInfo

trait Routes[F[_]] {
  def routes: HttpApp[F]
}

object Routes {
  def default[F[_]: Sync](): Routes[F] =
    new Http4sRoutes[F]()

  class Http4sRoutes[F[_]: Sync]
    extends Routes[F]
    with Http4sDsl[F] {

    implicit def entityEncoder[A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

    def opsRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
      case GET -> Root  => Ok("Welcome to REST servce in functional Scala!")
      case GET -> Root / "version_info" =>
        Ok(
          VersionInfo(
            name = "name"
          , version = "version"
          , scalaVersion = "scalaVersion"
          , sbtVersion = "sbtVersion"
          , gitCommitHash = "gitCommitHash"
          , gitCommitMessage = "gitCommitMessage"
          , gitCommitDate = "gitCommitDate"
          , gitCurrentBranch = "gitCurrentBranch"
          )
        )
    }

    override def routes: HttpApp[F] =
      opsRoutes.orNotFound
  }

}

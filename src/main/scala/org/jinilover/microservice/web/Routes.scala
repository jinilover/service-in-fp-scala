package org.jinilover.microservice.web

import cats.syntax.semigroupk._

import cats.effect.Sync

import io.circe.{Encoder}

import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.{EntityEncoder, EntityDecoder, HttpApp, HttpRoutes}

import org.jinilover.microservice.OpsTypes.VersionInfo
import org.jinilover.microservice.ops.OpsService

trait Routes[F[_]] {
  def routes: HttpApp[F]
}

object Routes {
  def default[F[_]: Sync](opsService: OpsService): Routes[F] =
    new Http4sRoutes[F](opsService)

  class Http4sRoutes[F[_]: Sync](opsService: OpsService)
    extends Routes[F]
    with Http4sDsl[F] {

    implicit def entityEncoder[A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

//    implicit def entityDecoder[A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

    def opsRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
      case GET -> Root  => Ok(opsService.welcomeMsg())
      case GET -> Root / "version_info" => Ok(opsService.versionInfo())


        // just for experiment
//      case req@POST -> Root / "post_version_info" =>
//        req.decode[VersionInfo] { verInfo =>
//          Ok(verInfo)
//        }
    }

    def serviceRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
      case req@POST -> Root / "users" / userId / "links" =>
        req.decode[String] { targetId =>
          Ok(s"targetId = $targetId")
        }

      case GET -> Root / "users" / userId / "links" =>
        Ok(s"Get all links of $userId")

      case GET -> Root / "links" / linkId =>
        Ok(s"Get details of $linkId")

      case req@PUT -> Root / "links" =>
        req.decode[String] { linkId =>
          Ok(s"confirmed linkId = $linkId")
        }

      case req@DELETE -> Root / "links" =>
        req.decode[String] { linkId =>
          Ok(s"delete linkId = $linkId")
        }

    }

    override def routes: HttpApp[F] =
      (opsRoutes <+> serviceRoutes).orNotFound
  }

}

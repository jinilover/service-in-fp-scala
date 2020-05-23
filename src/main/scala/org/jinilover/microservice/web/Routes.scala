package org.jinilover
package microservice
package web

import cats.syntax.semigroupk._
import cats.syntax.flatMap._
import cats.syntax.monadError._

import cats.effect.Sync

import io.circe.Encoder

import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.{EntityEncoder, HttpApp, HttpRoutes, QueryParamDecoder}

import org.jinilover.microservice.ops.OpsService
import org.jinilover.microservice.{InputError, LinkStatus, ServerError}
import org.jinilover.microservice.LinkTypes._
import org.jinilover.microservice.link.LinkService

trait Routes[F[_]] {
  def routes: HttpApp[F]
}

object Routes {
  def default[F[_]: Sync](opsService: OpsService
                        , linkService: LinkService[F]): Routes[F] =
    new Http4sRoutes[F](opsService, linkService)

  class Http4sRoutes[F[_]](opsService: OpsService
                        , linkService: LinkService[F])(implicit F: Sync[F])
    extends Routes[F]
    with Http4sDsl[F] {

    object OptionalStatusQueryParamMatcher extends OptionalQueryParamDecoderMatcher[LinkStatus]("status")
    implicit val statusQueryParamDecoder: QueryParamDecoder[LinkStatus] =
      QueryParamDecoder[String].map(toLinkStatus)

    // true means the user initiates the link and o.w.
    object OptionalIsUserInitiatorQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("is_initiator")

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
          linkService.addLink(UserId(userId), UserId(targetId))
            .redeemWith(
              {
                case InputError(err) => BadRequest(err)
                case ServerError(err) => InternalServerError(err)
              },
              linkId => Ok(linkId)
            )
        }

      case GET -> Root / "users" / userId / "links"
        :? OptionalStatusQueryParamMatcher(linkStatus)
        :? OptionalIsUserInitiatorQueryParamMatcher(isInitiator) =>
        val statusMsg = linkStatus.map(s => s"for $s").getOrElse("")
        Ok(s"Get all links of $userId $statusMsg")

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

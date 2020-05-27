package org.jinilover
package microservice
package web

import cats.data.{Kleisli, OptionT}
import cats.syntax.semigroupk._
import cats.syntax.monadError._
import cats.syntax.flatMap._
import cats.syntax.apply._

import cats.effect.{Sync}

import io.circe.{Decoder, Encoder}

import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s._
import org.http4s.server.AuthMiddleware

import ops.OpsService
import LinkTypes.{LinkId, LinkStatus, UserId, taggedTypeDecoder, taggedTypeEncoder, toLinkStatus}
import service.LinkService

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

    implicit def entityDecoder[A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

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
        req.decode[UserId] { targetId =>
          linkService
            .addLink(UserId(userId), targetId)
            .redeemWith(
              { case InputError(msg) => BadRequest(msg) },
              linkId => Ok(linkId)
            )
        }

      case GET -> Root / "users" / userId / "links"
        :? OptionalStatusQueryParamMatcher(linkStatusOps)
        :? OptionalIsUserInitiatorQueryParamMatcher(isInitiatorOps) =>
        linkService
          .getLinks(UserId(userId), linkStatusOps, isInitiatorOps)
          .flatMap(linkIds => Ok(linkIds))

      case GET -> Root / "links" / linkId =>
        linkService
          .getLink(LinkId(linkId))
          .flatMap(optLink => Ok(optLink.toList))

      case PUT -> Root / "links" / linkId =>
        linkService.acceptLink(LinkId(linkId)) *> Ok(s"LinkId $linkId accepted")
    }

    def authedRoutes: AuthedRoutes[UserId, F] = AuthedRoutes.of {
      // a prototype of using `AuthMiddleware` to authenticate the request
      // more things can be done from `LinkService` such as the `userId`
      // should be part of the link
      case DELETE -> Root / "links" / linkId as userId =>
        linkService.removeLink(LinkId(linkId)).flatMap(msg => Ok(msg))
    }

    val authUser: Kleisli[OptionT[F, *], Request[F], UserId] =
      Kleisli { req =>
        val userIdOpt =
          req.headers.get("Authorization".ci)
            .flatMap { header =>
              val v = header.value
              val bearer = "Bearer "
              if (v.startsWith(bearer) && v.trim != "Bearer")
                Some(UserId(v.replaceFirst(bearer, "").trim))
              else
                None
            }
        OptionT(F.pure(userIdOpt))
      }

    val authMidleware: AuthMiddleware[F, UserId] = AuthMiddleware(authUser)

    override def routes: HttpApp[F] =
      (opsRoutes <+> serviceRoutes <+> authMidleware(authedRoutes)).orNotFound
  }

}

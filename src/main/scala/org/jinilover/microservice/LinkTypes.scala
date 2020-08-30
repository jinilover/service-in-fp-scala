package org.jinilover
package microservice

import java.time.Instant

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }

import scalaz.{ @@, Tag }

object LinkTypes {
  type UserId = String @@ UserId.Marker
  object UserId extends Tagger[String]

  type LinkId = String @@ LinkId.Marker
  object LinkId extends Tagger[String]

  implicit def taggedTypeEncoder[A, T](implicit EA: Encoder[A]): Encoder[A @@ T] =
    EA.contramap(Tag.unwrap)

  implicit def taggedTypeDecoder[A, T](implicit DA: Decoder[A]): Decoder[A @@ T] =
    DA.map(Tag.apply[A, T])

  sealed trait LinkStatus
  object LinkStatus {
    case object Pending extends LinkStatus
    case object Accepted extends LinkStatus

    implicit def linkStatusEncoder: Encoder[LinkStatus] =
      implicitly[Encoder[String]].contramap(_.toString)

    implicit def linkStatusDecoder: Decoder[LinkStatus] =
      implicitly[Decoder[String]].map { s =>
        if (Pending.toString == s) Pending else Accepted
      }
  }

  case class Link(
    id: Option[LinkId] = None,
    initiatorId: UserId,
    targetId: UserId,
    status: LinkStatus,
    creationDate: Instant,
    confirmDate: Option[Instant] = None,
    uniqueKey: Option[String] = None
  )

  object Link {
    implicit val linkEncoder: Encoder[Link] = deriveEncoder
    implicit val linkDecoder: Decoder[Link] = deriveDecoder
  }

  case class SearchLinkCriteria(
    userId: UserId,
    linkStatus: Option[LinkStatus] = None,
    isInitiator: Option[Boolean] = None
  )

  def linkKey(userIds: UserId*): String =
    userIds.map(_.unwrap).sorted.mkString("_")

  def toLinkStatus(s: String): LinkStatus =
    if (s.toUpperCase == LinkStatus.Pending.toString.toUpperCase)
      LinkStatus.Pending
    else
      LinkStatus.Accepted

}

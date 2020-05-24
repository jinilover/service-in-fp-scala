package org.jinilover
package microservice

import java.time.Instant

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}

import scalaz.{@@, Tag}

object LinkTypes {
  type UserId = String @@ UserId.Marker
  object UserId extends Tagger[String]

  type LinkId = String @@ LinkId.Marker
  object LinkId extends Tagger[String]

  implicit def taggedTypeEncoder[A, T](implicit EA: Encoder[A]): Encoder[A @@ T] =
    EA.contramap(Tag.unwrap)

  implicit def taggedTypeDecoder[A, T](implicit DA: Decoder[A]): Decoder[A @@ T] =
    DA.map(Tag.apply[A, T])

  case class Link(id: Option[LinkId] = None
                , initiatorId: UserId
                , targetId: UserId
                , status: LinkStatus
                , creationDate: Instant
                , confirmDate: Option[Instant] = None
                , uniqueKey: Option[String] = None
               )

  case class SearchLinkCriteria(userId: UserId
                              , linkStatus: Option[LinkStatus] = None
                              , isInitiator: Option[Boolean] = None
                             )

  implicit val linkEncoder: Encoder[Link] = deriveEncoder

  def linkKey(userIds: UserId*): String =
    userIds.map(_.unwrap).sorted.mkString("_")
}

sealed trait LinkStatus
object LinkStatus {
  case object Pending extends LinkStatus
  case object Accepted extends LinkStatus

  implicit def linkStatusEncoder: Encoder[LinkStatus] =
    implicitly[Encoder[String]].contramap(_.toString)
}

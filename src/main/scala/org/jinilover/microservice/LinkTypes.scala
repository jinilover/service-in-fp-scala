package org.jinilover.microservice

import java.time.Instant

import io.circe.{Decoder, Encoder}

import scalaz.{@@, Tag}

import org.jinilover.Tagger

object LinkTypes {
  type UserId = String @@ UserId.Marker
  object UserId extends Tagger[String]

  type LinkId = String @@ LinkId.Marker
  object LinkId extends Tagger[String]

  implicit def taggedTypeEncoder[A, T](implicit EA: Encoder[A]): Encoder[A @@ T] =
    EA.contramap(Tag.unwrap)

  implicit def taggedTypeDecoder[A, T](implicit DA: Decoder[A]): Decoder[A @@ T] =
    DA.map(Tag.apply[A, T])

  case class Link(id: Option[LinkId]
                  , initiatorId: UserId
                  , targetId: UserId
                  , status: LinkStatus
                  , creationDate: Option[Instant]
                  , confirmDate: Option[Instant]
                  , uniqueKey: String)
}

sealed trait LinkStatus
object LinkStatus {
  case object Pending extends LinkStatus
  case object Accepted extends LinkStatus

  implicit def linkStatusEncoder: Encoder[LinkStatus] =
    implicitly[Encoder[String]].contramap(_.toString)
}

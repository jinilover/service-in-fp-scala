package org.jinilover.microservice

import io.circe.{Decoder, Encoder}
import scalaz.{@@, Tag}

object UserTypes {
  type UserId = String @@ UserId.Marker
  object UserId extends Tagger[String]

  type LinkId = String @@ LinkId.Marker
  object LinkId extends Tagger[String]

  implicit def taggedTypeEncoder[A, T](implicit EA: Encoder[A]): Encoder[A @@ T] =
    EA.contramap(Tag.unwrap)

  implicit def taggedTypeDecoder[A, T](implicit DA: Decoder[A]): Decoder[A @@ T] =
    DA.map(Tag.apply[A, T])
}

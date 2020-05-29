package org.jinilover.microservice

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}

package object web {
  implicit def entityEncoder[A: Encoder, F[_]: Sync]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

  implicit def entityDecoder[A: Decoder, F[_]: Sync]: EntityDecoder[F, A] = jsonOf[F, A]
}

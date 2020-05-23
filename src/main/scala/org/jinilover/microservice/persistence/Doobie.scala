package org.jinilover
package microservice
package persistence

import scala.reflect.runtime.universe.TypeTag

import scalaz.{@@, Tag}

import doobie._

import org.jinilover.microservice.LinkTypes.{LinkId, UserId}

object Doobie {
  private def taggedMeta[A: Meta: TypeTag, T: TypeTag]: Meta[A @@ T] =
    Meta[A].timap(Tag.apply[A, T])(_.unwrap)

  implicit val LinkStatusMeta: Meta[LinkStatus] =
    Meta[String].timap(toLinkStatus)(_.toString)

  implicit val LinkIdMeta: Meta[LinkId] =
    taggedMeta[String, LinkId.Marker]

  implicit val UserIdMeta: Meta[UserId] =
    taggedMeta[String, UserId.Marker]
}

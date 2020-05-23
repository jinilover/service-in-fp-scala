package org.jinilover.microservice.link

import cats.MonadError
import org.jinilover.microservice.InputError
import org.jinilover.microservice.LinkTypes.{LinkId, UserId}

trait LinkService[F[_]] {
  def addLink(initiatorId: UserId, targetId: UserId): F[LinkId]
}

object LinkService {
  def default[F[_]](implicit ME: MonadError[F, Throwable]): LinkService[F] =
    new LinkServiceImpl[F]

  class LinkServiceImpl[F[_]](implicit ME: MonadError[F, Throwable]) extends LinkService[F] {
    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] =
      if (initiatorId == targetId)
        ME.raiseError(InputError("Both user ids are the same"))
      else
        ME.pure(LinkId(s"${initiatorId}_${targetId}"))
  }
}

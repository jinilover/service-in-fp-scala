package org.jinilover
package microservice.link

import java.time.Clock

import cats.MonadError
import cats.syntax.monadError._
import cats.syntax.functor._
import cats.syntax.flatMap._

import org.jinilover.microservice.{InputError, LinkStatus, ThrowableError}
import org.jinilover.microservice.LinkTypes._
import org.jinilover.microservice.persistence.LinkPersistence

trait LinkService[F[_]] {
  def addLink(initiatorId: UserId, targetId: UserId): F[LinkId]
  def acceptLink(linkId: LinkId): F[Unit]
  def getLink(id: LinkId): F[Option[Link]]
}

object LinkService {
  def default[F[_]]
    (persistence: LinkPersistence[F], clock: Clock)
    (implicit F: MonadError[F, Throwable]): LinkService[F] =
    new LinkServiceImpl[F](persistence, clock)

  class LinkServiceImpl[F[_]]
    (persistence: LinkPersistence[F], clock: Clock)
    (implicit F: MonadError[F, Throwable])
    extends LinkService[F] {

    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] =
      if (initiatorId == targetId)
        F.raiseError(InputError("Both user ids are the same"))
      else {
        val link = Link(
          initiatorId = initiatorId
        , targetId = targetId
        , status = LinkStatus.Pending
        , creationDate = clock.instant)

        persistence.add(link)
          .redeemWith(
            err => F.raiseError {
              if (err.getMessage.toLowerCase contains """violates unique constraint "unique_unique_key"""")
                InputError(s"Link between ${initiatorId.unwrap} and ${targetId.unwrap} already exists")
              else
                err
            },
            F.pure
          )
      }

    override def getLink(id: LinkId): F[Option[Link]] =
      persistence.get(id)

    override def acceptLink(linkId: LinkId): F[Unit] =
      persistence.update(linkId, confirmDate = clock.instant(), status = LinkStatus.Accepted)
  }
}

package org.jinilover
package microservice
package link

import java.time.Clock

import cats.MonadError
import cats.syntax.monadError._
import cats.syntax.functor._

import LinkTypes.{UserId, LinkId, Link, SearchLinkCriteria}
import persistence.LinkPersistence

trait LinkService[F[_]] {
  def addLink(initiatorId: UserId, targetId: UserId): F[LinkId]
  def acceptLink(id: LinkId): F[Unit]
  def getLink(id: LinkId): F[Option[Link]]
  def removeLink(id: LinkId): F[String]
  def getLinks(userId: UserId
             , linkStatusOpt: Option[LinkStatus]
             , isInitiatorOps: Option[Boolean]): F[List[LinkId]]
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

    override def acceptLink(id: LinkId): F[Unit] =
      persistence.update(id, confirmDate = clock.instant(), status = LinkStatus.Accepted)

    override def removeLink(id: LinkId): F[String] =
      persistence.remove(id).map {
        case 0 => s"No need to remove non-exist linkid ${id.unwrap}"
        case _ => s"Linkid ${id.unwrap} removed successfully"
      }

    override def getLinks(userId: UserId
                        , linkStatusOpt: Option[LinkStatus]
                        , isInitiatorOps: Option[Boolean]): F[List[LinkId]] =
      persistence.getLinks(
        SearchLinkCriteria(userId, linkStatusOpt, isInitiatorOps)
      )
  }
}

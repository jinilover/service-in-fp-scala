package org.jinilover
package microservice
package service

import java.time.Clock

import cats.MonadError
import cats.syntax.monadError._
import cats.syntax.flatMap._
import cats.syntax.apply._

import LinkTypes.{UserId, LinkId, Link, LinkStatus, SearchLinkCriteria}
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
    ( persistence: LinkPersistence[F]
    , clock: Clock
    , log: Log[F] )
    (implicit F: MonadError[F, Throwable]): LinkService[F] =
    new LinkServiceImpl[F](persistence, clock, log)

  class LinkServiceImpl[F[_]]
    ( persistence: LinkPersistence[F]
    , clock: Clock
    , log: Log[F] )
    (implicit F: MonadError[F, Throwable])
    extends LinkService[F] {

    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] =
      if (initiatorId == targetId) {
        val err = InputError("Both user ids are the same")
        log.error(err) *> F.raiseError(err)
      }
      else {
        val link = Link(
          initiatorId = initiatorId
        , targetId = targetId
        , status = LinkStatus.Pending
        , creationDate = clock.instant)

        persistence.add(link)
          .redeemWith(
            err => {
              if (err.getMessage.toLowerCase contains """violates unique constraint "unique_unique_key"""") {
                val inputErr = InputError(s"Link between ${initiatorId.unwrap} and ${targetId.unwrap} already exists")
                log.warn(inputErr.msg) *> F.raiseError(inputErr)
              }
              else
                F.raiseError(err)
            },
            F.pure
          )
      }

    override def getLink(id: LinkId): F[Option[Link]] =
      persistence.get(id)

    override def acceptLink(id: LinkId): F[Unit] =
      // TODO better to change db to return Int and raise error if it return 0
      persistence.update(id, confirmDate = clock.instant(), status = LinkStatus.Accepted) *> F.pure(())
//    persistence.update(id, confirmDate = clock.instant(), status = LinkStatus.Accepted).flatMap {
//      case 0 =>
//        val inputErr = InputError(s"Fails to accpet non-exist linkid ${id.unwrap}")
//        log.warn(inputErr.msg) *> F.raiseError(inputErr)
//      case _ => F.pure(s"Linkid ${id.unwrap} removed successfully")
//    }

    override def removeLink(id: LinkId): F[String] =
      persistence.remove(id).flatMap {
        case 0 =>
          val inputErr = InputError(s"Fails to remove non-exist linkid ${id.unwrap}")
          log.warn(inputErr.msg) *> F.raiseError(inputErr)
        case _ => F.pure(s"Linkid ${id.unwrap} removed successfully")
      }

    override def getLinks(userId: UserId
                        , linkStatusOpt: Option[LinkStatus]
                        , isInitiatorOps: Option[Boolean]): F[List[LinkId]] =
      persistence.getLinks(
        SearchLinkCriteria(userId, linkStatusOpt, isInitiatorOps)
      )
  }
}

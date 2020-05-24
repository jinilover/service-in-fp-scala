package org.jinilover
package microservice
package persistence

import java.time.{Clock, Instant}
import java.util.UUID

import cats.effect.IO
import cats.syntax.flatMap._

import doobie.Transactor
import doobie.syntax.connectionio._
import doobie.syntax.string._
import doobie.Fragments.andOpt

import LinkTypes._
import Doobie._

trait LinkPersistence[F[_]] {
  def add(link: Link): F[LinkId]
  def get(id: LinkId): F[Option[Link]]
  def getLinks(srchCriteria: SearchLinkCriteria): F[List[LinkId]]
}

object LinkPersistence {
  def default(xa: Transactor[IO], clock: Clock): LinkPersistence[IO] =
    new LinkDoobie(xa, clock)

  class LinkDoobie(xa: Transactor[IO], clock: Clock) extends LinkPersistence[IO] {
    override def add(link: Link): IO[LinkId] = {
      val Link(id, initiatorId, targetId, _, creationDateOpt, confirmDate, _) = link
      val linkId = id getOrElse LinkId(UUID.randomUUID.toString)
      val status: LinkStatus = LinkStatus.Pending
      val creationDate = creationDateOpt getOrElse Instant.now(clock)
      val uniqueKey = linkKey(initiatorId, targetId)

      sql"""
            INSERT INTO links (id, initiator_id, target_id, status, creation_date, confirm_date, unique_key)
            VALUES ($linkId, $initiatorId, $targetId, $status, $creationDate, $confirmDate, $uniqueKey)
         """.update.run.transact(xa) >> IO(linkId)
    }

    override def get(id: LinkId): IO[Option[Link]] = {
      sql"""
            SELECT id, initiator_id, target_id, status, creation_date, confirm_date, unique_key
            FROM links
            WHERE id = $id
        """.query[Link].option.transact(xa)
    }

    override def getLinks(srchCriteria: SearchLinkCriteria): IO[List[LinkId]] = {
      val SearchLinkCriteria(userId, linkStatus, isInitiator) = srchCriteria

      val byUserId = isInitiator.map { bool =>
        val srchColumn = if (bool) "initiator_id" else "target_id"
        fr"$srchColumn = $userId"
      }.getOrElse(
        fr"(initiator_id = $userId OR target_id = $userId)"
      )

      val byLinkStatus = linkStatus.map(v => fr"status = $v")

      val fragment =
        fr"""
          SELECT id FROM links
          WHERE
        """ ++ byUserId ++ andOpt(byLinkStatus)

      fragment.query[LinkId].to[List].transact(xa)
    }
  }

}

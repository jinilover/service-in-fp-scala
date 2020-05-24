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
import doobie.Fragments.{whereAndOpt}

import LinkTypes._
import Doobie._

trait LinkPersistence[F[_]] {
  def add(link: Link): F[LinkId]
  def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): F[Unit]
  def get(id: LinkId): F[Option[Link]]
  def getLinks(srchCriteria: SearchLinkCriteria): F[List[LinkId]]
  def remove(id: LinkId): F[Int]
}

object LinkPersistence {
  def default(xa: Transactor[IO]): LinkPersistence[IO] =
    new LinkDoobie(xa)

  class LinkDoobie(xa: Transactor[IO]) extends LinkPersistence[IO] {
    override def add(link: Link): IO[LinkId] = {
      val Link(_, initiatorId, targetId, status, creationDate, _, _) = link
      val linkId = LinkId(UUID.randomUUID.toString)
      val uniqueKey = linkKey(initiatorId, targetId)

      sql"""
            INSERT INTO links (id, initiator_id, target_id, status, creation_date, unique_key)
            VALUES ($linkId, $initiatorId, $targetId, $status, $creationDate, $uniqueKey)
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
        if (bool) fr"initiator_id = $userId" else fr"target_id = $userId"
      }.orElse(
        Some(fr"(initiator_id = $userId OR target_id = $userId)")
      )

      val byLinkStatus = linkStatus.map(v => fr"status = $v")

      val fragment =
        fr"""
          SELECT id FROM links
        """ ++ whereAndOpt(byUserId, byLinkStatus)

      fragment.query[LinkId].to[List].transact(xa)
    }

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): IO[Unit] = {
      sql"""
          UPDATE links
          set status = $status, confirm_date = $confirmDate
          where id = $linkId
        """.update.run.transact(xa).void
    }

    override def remove(id: LinkId): IO[Int] = {
      sql"""
          DELETE FROM links
          WHERE id = $id
        """.update.run.transact(xa)
    }
  }

}

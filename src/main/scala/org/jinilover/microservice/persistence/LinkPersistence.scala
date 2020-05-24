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

import LinkTypes.{Link, LinkId}
import Doobie._

trait LinkPersistence[F[_]] {
  def add(link: Link): F[LinkId]
  def get(id: LinkId): F[Option[Link]]
}

object LinkPersistence {
  def default(xa: Transactor[IO], clock: Clock): LinkPersistence[IO] =
    new LinkDoobie(xa, clock)

  class LinkDoobie(xa: Transactor[IO], clock: Clock) extends LinkPersistence[IO] {
    override def add(link: Link): IO[LinkId] = {
      val Link(id, initiatorId, targetId, status, creationDateOpt, confirmDate, uniqueKey) = link
      val linkId = id getOrElse LinkId(UUID.randomUUID.toString)
      val creationDate = creationDateOpt getOrElse Instant.now(clock)

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
  }

}

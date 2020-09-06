package org.jinilover.microservice

import java.time.Instant
import java.util.UUID

import doobie.Transactor
import doobie.syntax.connectionio._
import doobie.syntax.string._
import doobie.Fragments.whereAndOpt

import zio._
import zio.interop.catz._

import org.jinilover.microservice.LinkTypes._
import org.jinilover.microservice.persistence.Doobie._

package object persistence {
  type LinkStore_ = Has[LinkStore_.Service]

  //TODO remove underscore
  object LinkStore_ {

    trait Service {
      def add(link: Link): Task[LinkId]

      def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): Task[Int]

      def get(id: LinkId): Task[Option[Link]]

      def getByUniqueKey(uid1: UserId, uid2: UserId): Task[Option[Link]]

      def getLinks(srchCriteria: SearchLinkCriteria): Task[List[LinkId]]

      def remove(id: LinkId): Task[Int]
    }

    val live: ZLayer[Has[Transactor[Task]], Throwable, LinkStore_] =
      ZLayer.fromService[Transactor[Task], Service] { xa =>
        new Service {
          override def add(link: Link): Task[LinkId] =
            for {
              linkId <- Task.effect(LinkId(UUID.randomUUID.toString))
              Link(_, initiatorId, targetId, status, creationDate, _, _) = link
              uniqueKey = linkKey(initiatorId, targetId)
              _      <- sql"""INSERT INTO links (id, initiator_id, target_id, status, creation_date, unique_key)
                            VALUES ($linkId, $initiatorId, $targetId, $status, $creationDate, $uniqueKey)
                         """.update.run.transact(xa)
            } yield linkId

          override def get(id: LinkId): Task[Option[Link]] =
            sql"""
                SELECT id, initiator_id, target_id, status, creation_date, confirm_date, unique_key
                FROM links
                WHERE id = $id
            """.query[Link].option.transact(xa)

          override def getByUniqueKey(uid1: UserId, uid2: UserId): Task[Option[Link]] = {
            val uniqueKey = linkKey(uid1, uid2)
            sql"""
              SELECT id, initiator_id, target_id, status, creation_date, confirm_date, unique_key
              FROM links
              WHERE unique_key = $uniqueKey
            """.query[Link].option.transact(xa)
          }

          override def getLinks(srchCriteria: SearchLinkCriteria): Task[List[LinkId]] = {
            val SearchLinkCriteria(userId, linkStatus, isInitiator) = srchCriteria

            lazy val userIsInitiator = fr"initiator_id = $userId"
            lazy val userIsTarget = fr"target_id = $userId"

            val byUserId = isInitiator.map { bool =>
              if (bool) userIsInitiator else userIsTarget
            }.orElse(Some(fr"(" ++ userIsInitiator ++ fr" OR " ++ userIsTarget ++ fr")"))

            val byLinkStatus = linkStatus.map(v => fr"status = $v")

            val fragment =
              fr"""
                SELECT id FROM links
              """ ++ whereAndOpt(byUserId, byLinkStatus)

            fragment.query[LinkId].to[List].transact(xa)
          }

          override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): Task[Int] =
            sql"""
              UPDATE links
              set status = $status, confirm_date = $confirmDate
              where id = $linkId
            """.update.run.transact(xa)

          override def remove(id: LinkId): Task[Int] =
            sql"""
              DELETE FROM links
              WHERE id = $id
            """.update.run.transact(xa)
        }
      }
  }
}

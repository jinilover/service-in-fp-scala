package org.jinilover.microservice

import java.util.UUID

import doobie.Transactor
import doobie.syntax.connectionio._
import doobie.syntax.string._

import zio._
import zio.interop.catz._

import org.jinilover.microservice.LinkTypes._
import org.jinilover.microservice.persistence.Doobie._

package object persistence {
  type LinkStore_ = Has[LinkStore_.Service]

  object LinkStore_ {
    trait Service {
      def add(link: Link): Task[LinkId]
    }

    val live: ZLayer[Has[Transactor[Task]], Throwable, LinkStore_] =
      ZLayer.fromService[Transactor[Task], Service] { transactor =>
        new Service {
          override def add(link: Link): Task[LinkId] = {
            val Link(_, initiatorId, targetId, status, creationDate, _, _) = link
            val linkId = LinkId(UUID.randomUUID.toString)
            val uniqueKey = linkKey(initiatorId, targetId)

            sql"""
                INSERT INTO links (id, initiator_id, target_id, status, creation_date, unique_key)
                VALUES ($linkId, $initiatorId, $targetId, $status, $creationDate, $uniqueKey)
             """.update.run.transact(transactor) *> Task.effect(linkId)
          }
        }
      }
  }

}

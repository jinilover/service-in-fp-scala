package org.jinilover.microservice.persistence

import java.time.{ Clock, Instant}

import cats.effect.IO

import doobie.util.transactor.Transactor

import org.jinilover.microservice.LinkTypes.{Link, LinkId}

trait LinkPersistence[F[_]] {
  def add(link: Link): F[LinkId]
}

object LinkPersistence {
  def default(xa: Transactor[IO], clock: Clock): LinkPersistence[IO] =
    ???

  class LinkDoobie(xa: Transactor[IO], clock: Clock) extends LinkPersistence[IO] {
    override def add(link: Link): IO[LinkId] = ???
  }

}

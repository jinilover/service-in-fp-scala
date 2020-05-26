package org.jinilover.microservice

import java.time.{Clock, Instant}

import cats.effect.IO

import cats.syntax.flatMap._

import LinkTypes.{Link, LinkId, LinkStatus, SearchLinkCriteria, UserId, linkKey}
import link.LinkService
import persistence.LinkPersistence

object Mock {
  val clock = Clock.systemDefaultZone()

  // sample user id
  val List(mikasa, eren, armin, annie, reiner, bert, levi, erwin) =
    List("mikasa", "eren", "armin", "annie", "reiner", "bert", "levi", "erwin").map(UserId.apply)

  // sample links
  val List(
      mika_add_eren
    , reiner_add_eren
    , bert_add_eren
    , eren_add_armin
    , eren_add_annie
    , eren_add_levi
    , eren_add_erwin) =
    List( (mikasa, eren)
      , (reiner, eren)
      , (bert, eren)
      , (eren, armin)
      , (eren, annie)
      , (eren, levi)
      , (eren, erwin) )
      .map { case (initiator, target) =>
        Link(initiatorId = initiator
          , targetId = target
          , status = LinkStatus.Pending
          , creationDate = clock.instant)
      }

  val erenSearchCriteria = SearchLinkCriteria(userId = eren)

  val dummyLinkId = LinkId("dummy_linkId")

  // mock persistence
  class DummyPersistence[F[_]] extends LinkPersistence[F] {
    override def add(link: LinkTypes.Link): F[LinkId] = ???

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): F[Unit] = ???

    override def get(id: LinkId): F[Option[LinkTypes.Link]] = ???

    override def getLinks(srchCriteria: LinkTypes.SearchLinkCriteria): F[List[LinkId]] = ???

    override def remove(id: LinkId): F[Int] = ???
  }

  // To test how its user handle unique key violation
  class MockDbViolateUniqueKey(sampleLinkId: LinkId) extends DummyPersistence[IO] {
    var linkSet = Set.empty[String]

    override def add(link: LinkTypes.Link): IO[LinkId] = {
      val uniqueKey = linkKey(link.initiatorId, link.targetId)
      if (linkSet contains uniqueKey)
        IO.raiseError(new RuntimeException("""violates unique constraint "unique_unique_key""""))
      else {
        linkSet += uniqueKey
        IO.pure(sampleLinkId)
      }
    }
  }

  class MockDbForUpdateLink extends DummyPersistence[IO] {
    var linkId: LinkId = LinkId("")
    var confirmDate: Instant = Instant.ofEpochMilli(0L)
    var status: LinkStatus = LinkStatus.Pending

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): IO[Unit] = {
      IO(println("update called")) >>
        IO(this.linkId = linkId) >>
        IO(this.confirmDate = confirmDate) >>
        IO(this.status = status)
    }
  }

  class MockDbForRemoveLink extends DummyPersistence[IO] {
    var count = 1
    override def remove(id: LinkId): IO[Int] = {
      // note that `count = 1` is returned even though it decremented by 1 in the end
      IO(count) <* IO(count -= 1)
    }
  }

  class MockDbForGetLinks(linkIds: List[LinkId]) extends DummyPersistence[IO] {
    var searchCriteria = erenSearchCriteria

    override def getLinks(srchCriteria: LinkTypes.SearchLinkCriteria): IO[List[LinkId]] =
      IO(searchCriteria = srchCriteria) >> IO(linkIds)
  }

  class MockDbForGetLink(cache: Map[LinkId, Link]) extends DummyPersistence[IO] {
    override def get(id: LinkId): IO[Option[LinkTypes.Link]] =
      IO(cache.get(id))
  }


  // mock service
  class DummyService[F[_]] extends LinkService[F] {
    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] = ???

    override def acceptLink(id: LinkId): F[Unit] = ???

    override def getLink(id: LinkId): F[Option[Link]] = ???

    override def removeLink(id: LinkId): F[String] = ???

    override def getLinks(userId: UserId, linkStatusOpt: Option[LinkStatus], isInitiatorOps: Option[Boolean]): F[List[LinkId]] = ???
  }

  class MockServiceForAcceptLink extends DummyService[IO] {
    var linkId = LinkId("")

    override def acceptLink(id: LinkId): IO[Unit] =
      IO(this.linkId = id)
  }


}

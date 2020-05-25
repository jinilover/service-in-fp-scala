package org.jinilover.microservice

import java.time.{Clock, Instant}

import cats.effect.IO

import cats.syntax.flatMap._

import org.jinilover.microservice.LinkTypes.{Link, LinkStatus, LinkId, SearchLinkCriteria, UserId, linkKey}

import org.jinilover.microservice.persistence.LinkPersistence

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

  // mock implementation
  class DummyPersistence extends LinkPersistence[IO] {
    override def add(link: LinkTypes.Link): IO[LinkId] = ???

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): IO[Unit] = ???

    override def get(id: LinkId): IO[Option[LinkTypes.Link]] = ???

    override def getLinks(srchCriteria: LinkTypes.SearchLinkCriteria): IO[List[LinkId]] = ???

    override def remove(id: LinkId): IO[Int] = ???
  }

  // To test how its user handle unique key violation
  class MockDbViolateUniqueKey(sampleLinkId: LinkId) extends DummyPersistence {
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

  class MockDbForUpdateLink extends DummyPersistence {
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

  class MockDbForRemoveLink extends DummyPersistence {
    var count = 1
    override def remove(id: LinkId): IO[Int] = {
      // note that `count = 1` is returned even though it decremented by 1 in the end
      IO(count) <* IO(count -= 1)
    }
  }

  class MockDbForGetLinks(linkIds: List[LinkId]) extends DummyPersistence {
    var searchCriteria = erenSearchCriteria

    override def getLinks(srchCriteria: LinkTypes.SearchLinkCriteria): IO[List[LinkId]] =
      IO(searchCriteria = srchCriteria) >> IO(linkIds)
  }

  class MockDbForGetLink(cache: Map[LinkId, Link]) extends DummyPersistence {
    override def get(id: LinkId): IO[Option[LinkTypes.Link]] =
      IO(cache.get(id))
  }





}

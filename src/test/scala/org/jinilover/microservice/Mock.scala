package org.jinilover
package microservice

import java.time.{Clock, Instant}

import cats.effect.IO
import cats.syntax.flatMap._
import cats.syntax.apply._
import cats.{Monad, MonadError}
import cats.mtl.MonadState

import LinkTypes.{Link, LinkId, LinkStatus, SearchLinkCriteria, UserId, linkKey}
import service.LinkService
import persistence.LinkPersistence

object Mock {
  val clock = Clock.systemDefaultZone()

  // sample user id
  val sampleUserIds@List(mikasa, eren, armin, annie, reiner, bert, levi, erwin) =
    List("mikasa", "eren", "armin", "annie", "reiner", "bert", "levi", "erwin")
      .map(UserId.apply)

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
      , (eren, erwin)
    ).map(createNewLink)

  lazy val createNewLink: ((UserId, UserId)) => Link = {
    case (initiatorId, targetId) =>
      Link(initiatorId = initiatorId
        , targetId = targetId
        , status = LinkStatus.Pending
        , creationDate = clock.instant)
  }

  val erenSearchCriteria = SearchLinkCriteria(userId = eren)
  val possibleErenSearchCriterias = List[SearchLinkCriteria => SearchLinkCriteria](
      identity
    , _.copy(linkStatus = Some(LinkStatus.Accepted)) // search `Accepted` only
    , _.copy(linkStatus = Some(LinkStatus.Pending)) // search `Pending` only
    , _.copy(isInitiator = Some(true)) // the user is initiator only
    , _.copy(isInitiator = Some(false)) // the user target only
    , _.copy(isInitiator = Some(true), linkStatus = Some(LinkStatus.Accepted))
    , _.copy(isInitiator = Some(true), linkStatus = Some(LinkStatus.Pending))
    , _.copy(isInitiator = Some(false), linkStatus = Some(LinkStatus.Accepted))
    , _.copy(isInitiator = Some(false), linkStatus = Some(LinkStatus.Pending))
  ).map(f => f(erenSearchCriteria))

  val dummyLinkId = LinkId("dummy_linkId")

  // mock persistence
  class DummyPersistence[F[_]] extends LinkPersistence[F] {
    override def add(link: LinkTypes.Link): F[LinkId] = ???

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): F[Int] = ???

    override def get(id: LinkId): F[Option[LinkTypes.Link]] = ???

    override def getLinks(srchCriteria: LinkTypes.SearchLinkCriteria): F[List[LinkId]] = ???

    override def remove(id: LinkId): F[Int] = ???

    override def getByUniqueKey(uid1: UserId, uid2: UserId): F[Option[Link]] = ???
  }

  class MockDbForAddLink[F[_]]
    (linkId: LinkId)
    (implicit F: Monad[F])
    extends DummyPersistence[F] {

    override def add(link: LinkTypes.Link): F[LinkId] = F.pure(linkId)
  }

  class MockDbViolateUniqueKey[F[_]]
      (sampleLinkId: LinkId)
      (implicit MS: MonadState[F, Set[String]], ME: MonadError[F, Throwable])
    extends DummyPersistence[F] {

    override def add(link: LinkTypes.Link): F[LinkId] = {
      val uniqueKey = linkKey(link.initiatorId, link.targetId)
      MS.get.flatMap { set =>
        if (set contains uniqueKey)
          ME.raiseError(new RuntimeException("""violates unique constraint "unique_unique_key""""))
        else
          MS.set(set + uniqueKey) *> ME.pure(sampleLinkId)
      }
    }
  }

 class MockDbForUpdateLink[F[_]: Monad]
    (implicit MS: MonadState[F, (LinkId, Instant, LinkStatus)])
    extends DummyPersistence[F] {

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): F[Int] =
      MS.set(linkId, confirmDate, status) *> MS.monad.pure(1)
  }

  class MockDbForNoOfLinkUpdated[F[_]]
    (noOfLinkAccepted: Int)
    (implicit F: Monad[F])
    extends DummyPersistence[F] {

    override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): F[Int] =
      F.pure(noOfLinkAccepted)
  }

  class MockDbForNoOfLinkRemoved[F[_]: Monad]
    (noOfLinkRemoved: Int)
    (implicit F: Monad[F])
    extends DummyPersistence[F] {

    override def remove(id: LinkId): F[Int] =
      F.pure(noOfLinkRemoved)
  }

  class MockDbForGetLinks[F[_]: Monad]
    (implicit MS: MonadState[F, SearchLinkCriteria])
    extends DummyPersistence[F] {

    override def getLinks(srchCriteria: SearchLinkCriteria): F[List[LinkId]] =
      MS.set(srchCriteria) *> MS.monad.pure(Nil)
  }

  class MockDbForGetLink(cache: Map[LinkId, Link]) extends DummyPersistence[IO] {
    override def get(id: LinkId): IO[Option[LinkTypes.Link]] =
      IO(cache.get(id))
  }


  // mock service
  class DummyService[F[_]] extends LinkService[F] {
    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] = ???

    override def acceptLink(id: LinkId): F[String] = ???

    override def getLink(id: LinkId): F[Option[Link]] = ???

    override def removeLink(id: LinkId): F[String] = ???

    override def getLinks(userId: UserId, linkStatusOpt: Option[LinkStatus], isInitiatorOps: Option[Boolean]): F[List[LinkId]] = ???
  }

  class MockServiceForAcceptLink[F[_]: Monad]
    (implicit MS: MonadState[F, LinkId])
    extends DummyService[F] {

    override def acceptLink(id: LinkId): F[String] =
      MS.set(id) *> MS.monad.pure(s"Linkid ${id.unwrap} removed successfully")
  }

  class MockServiceForSuccessAddLink[F[_]: Monad]
    (linkId: LinkId)
    (implicit MS: MonadState[F, (UserId, UserId)])
    extends DummyService[F] {

    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] =
      MS.set((initiatorId, targetId)) *> MS.monad.pure(linkId)
  }

  class MockServiceForUniqueKeyViolation[F[_]]
    (implicit F: MonadError[F, Throwable])
    extends DummyService[F] {

    override def addLink(initiatorId: UserId, targetId: UserId): F[LinkId] =
      F.raiseError(
        InputError(s"Link between ${initiatorId.unwrap} and ${targetId.unwrap} already exists")
      )
  }

  class MockServiceForRemoveOneLink[F[_]]
    (implicit F: Monad[F])
    extends DummyService[F] {

    override def removeLink(id: LinkId): F[String] =
      F.pure(s"Linkid ${id.unwrap} removed successfully")
  }

  class MockServiceForRemoveZeroLink[F[_]]
    (implicit F: MonadError[F, Throwable])
    extends DummyService[F] {

    override def removeLink(id: LinkId): F[String] = {
      val inputErr = InputError(s"Fails to remove non-exist linkid ${id.unwrap}")
      F.raiseError(inputErr)
    }
  }

  // mock log
  class MockLogMonadStack[F[_], S]
    (implicit MS: MonadState[F, S]
     , ME: MonadError[F, Throwable])
      extends Log[F] {
    override def error(err: Error): F[Unit] = ME.pure(())
    override def warn(msg: String): F[Unit] = ME.pure(())
    override def info(msg: String): F[Unit] = ME.pure(())
    override def debug(msg: String): F[Unit] = ME.pure(())
  }

  class MockLogMonadState[F[_], S]
    (implicit MS: MonadState[F, S])
      extends Log[F] {
    override def error(err: Error): F[Unit] = MS.monad.pure(())
    override def warn(msg: String): F[Unit] = MS.monad.pure(())
    override def info(msg: String): F[Unit] = MS.monad.pure(())
    override def debug(msg: String): F[Unit] = MS.monad.pure(())
  }
}

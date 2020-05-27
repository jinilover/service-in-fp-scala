package org.jinilover
package microservice
package service

import java.time.{Clock, Instant}

import cats.data.{EitherT, StateT}
import cats.implicits._
import cats.mtl.implicits._

import cats.effect.IO

import org.specs2.{ScalaCheck, Specification}
import org.specs2.specification.core.SpecStructure

import LinkTypes.{Link, LinkId, LinkStatus, SearchLinkCriteria, UserId}
import Mock._
import LinkTypeArbitraries._

class LinkServiceSpec extends Specification with ScalaCheck {
  lazy val clock = Clock.systemDefaultZone()

  val dummyLinkId = LinkId("value doesn't matter")

  override def is: SpecStructure =
    s2"""
      LinkService
        should not allow user to link himself $addUser
        should handle unique key violation from db $handleUniqueKeyViolation
        should pass correct arguments in acceptLink $acceptLink
        should handle the value from db.remove properly $removeLink
        should form the required search criteria in getLinks $getLinks
        should pass the linkId to db in getLink $getLink
    """

  def addUser = prop { (uid1: UserId, uid2: UserId) =>
    val log = Log.default.unsafeRunSync()
    val mockDb = new MockDbForAddLink[IO](dummyLinkId)
    val service = LinkService.default[IO](mockDb, clock, log)
    val addIO = service.addLink(uid1, uid2)

    if (uid1 == uid2)
      addIO.unsafeRunSync() must
        throwAn[Error].like {case InputError(msg) => msg must be_==("Both user ids are the same")}
    else
      addIO.unsafeRunSync() must be_==(dummyLinkId)
  }

  //TODO scalacheck
  // to avoid using var, use MonadState for the persistence layer to maintain
  // the state of links added to the persistence
  def handleUniqueKeyViolation = {
    type MonadStack[A] = EitherT[StateT[IO, Set[String], ?], Throwable, A]

    val dummyLog = new MockLogMonadStack[MonadStack, Set[String]]
    val mockDb = new MockDbViolateUniqueKey[MonadStack](dummyLinkId)
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)

    val addLink = service.addLink(mikasa, eren)
    val initialState = Set.empty[String] // mimic the data stored by the persistence

    // 1st addLink should be success because the state is empty
    val (state2, result1) = addLink.value.run(initialState).unsafeRunSync()
    val expectedResult1 = Right(dummyLinkId)
    // 2nd addLink of the same user Ids should fails
    val (_, result2) = addLink.value.run(state2).unsafeRunSync()
    val expectedResult2 =
      Left(InputError(s"Link between ${mikasa.unwrap} and ${eren.unwrap} already exists"))

    (result1 must be_==(expectedResult1)) and
    (result2 must be_==(expectedResult2))
  }

  def acceptLink = {
    type S = (LinkId, Instant, LinkStatus)
    type MonadStack[A] = StateT[IO, S, A]

    val dummyLog = new MockLogMonadState[MonadStack, S]
    val mockDb = new MockDbForUpdateLink[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)
    // mimic the original cache maintained by mockDb
    val initialState = (LinkId(""), Instant.ofEpochMilli(0L), LinkStatus.Pending)

    // test to ensure linkId, time, status sent to mockDb correctly
    val ((linkId, time, status), _) =
      service.acceptLink(dummyLinkId).run(initialState).unsafeRunSync()

    (linkId must be_==(dummyLinkId)) and
      (Math.abs(time.getEpochSecond - clock.instant.getEpochSecond) must be ~(1L +/- 1L)) and
      (status must be_==(LinkStatus.Accepted))
  }

  def removeLink = {
    type MonadStack[A] = StateT[IO, Int, A]

    val dummyLog = new MockLogMonadState[MonadStack, Int]
    val mockDb = new MockDbForRemoveLink[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)

    // mimic there is 1 link originally inside mockDb
    // in the first run, mockDb return `state = 1` and decremented the state by 1
    // in the second run, mockDb return `state = 0`
    val (_, msgs) =
      List.fill(2)(dummyLinkId).traverse(service.removeLink).run(1).unsafeRunSync()
    val expectedMsgs = List(
      s"Linkid ${dummyLinkId.unwrap} removed successfully"
    , s"No need to remove non-exist linkid ${dummyLinkId.unwrap}"
    )

    msgs must be_==(expectedMsgs)
  }

  //TODO scalacheck
  def getLinks = prop { (uid: UserId, status: Option[LinkStatus], isInitiator: Option[Boolean]) =>
    type MonadStack[A] = StateT[IO, SearchLinkCriteria, A]

    val dummyLog = new MockLogMonadState[MonadStack, SearchLinkCriteria]
    val mockDb = new MockDbForGetLinks[MonadStack](Nil)
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)

    val initialState = erenSearchCriteria // any value is fine as it should be overwritten in the execution
    val (criteriaSentToDb, _) =
      service.getLinks(uid, status, isInitiator)
        .run(initialState)
        .unsafeRunSync()
    val expectedResult = SearchLinkCriteria(uid, status, isInitiator)

    criteriaSentToDb must be_==(expectedResult)
  }

  def getLink =
    (
      for {
        log <- Log.default
        dbCache: Map[LinkId, Link] = Map(dummyLinkId -> mika_add_eren)
        mockDb = new MockDbForGetLink(dbCache)
        service = LinkService.default(mockDb, clock, log)

        existLink <- service.getLink(dummyLinkId)
        nonExistLInk <- service.getLink(LinkId("non exist link"))
      } yield
        (existLink must beSome(mika_add_eren)) and
        (nonExistLInk must beNone)
    ).unsafeRunSync()
}

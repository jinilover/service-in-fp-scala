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

  // to avoid using var, use MonadState for the persistence layer to maintain
  // the state of links added to the persistence
  def handleUniqueKeyViolation = prop { (userIdPair: (UserId, UserId)) =>
    val (uid1, uid2) = userIdPair

    type MonadStack[A] = EitherT[StateT[IO, Set[String], ?], Throwable, A]

    val dummyLog = new MockLogMonadStack[MonadStack, Set[String]]
    val mockDb = new MockDbViolateUniqueKey[MonadStack](dummyLinkId)
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)

    val addLink = service.addLink(uid1, uid2)

    {
      for {
        // 1st addLink should be success because the state is empty
        (state2, result1) <- addLink.value.run(Set.empty[String])
        expectedResult1 = Right(dummyLinkId)
        // 2nd addLink should encounter unique key violation
        (_, result2) <- addLink.value.run(state2)
        expectedResult2 = Left(InputError(s"Link between ${uid1.unwrap} and ${uid2.unwrap} already exists"))
      } yield (result1 must be_==(expectedResult1)) and (result2 must be_==(expectedResult2))
    }.unsafeRunSync()
  }.setArbitrary(unequalUserIdsPairArbitrary)

  def acceptLink = {
    type S = (LinkId, Instant, LinkStatus)
    type MonadStack[A] = StateT[IO, S, A]

    val dummyLog = new MockLogMonadState[MonadStack, S]
    val mockDb = new MockDbForUpdateLink[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)
    val initialState = (LinkId(""), Instant.ofEpochMilli(0L), LinkStatus.Pending)

    // test to ensure linkId, time, status sent to mockDb correctly
    service.acceptLink(dummyLinkId).run(initialState).map { case ((linkId, time, status), _) =>
      (linkId must be_==(dummyLinkId)) and
      (Math.abs(time.getEpochSecond - clock.instant.getEpochSecond) must be ~(1L +/- 1L)) and
      (status must be_==(LinkStatus.Accepted))
    }.unsafeRunSync()
  }

  def removeLink = {
    type MonadStack[A] = StateT[IO, Int, A]

    val dummyLog = new MockLogMonadState[MonadStack, Int]
    val mockDb = new MockDbForRemoveLink[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)

    val removeLink = service.removeLink(dummyLinkId)
    // first run should return a msg of link removed successfully
    val (state2, result1) = removeLink.run(1).unsafeRunSync()
    // second run should throw error that link not exist
    val run2 = removeLink.run(state2)

    (result1 must be_==(s"Linkid ${dummyLinkId.unwrap} removed successfully")) and
    (run2.unsafeRunSync() must throwAn[InputError].like { case InputError(err) =>
      err must be_==(s"Fails to remove non-exist linkid ${dummyLinkId.unwrap}")
    })

  }

  def getLinks = prop { (uid: UserId, status: Option[LinkStatus], isInitiator: Option[Boolean]) =>
    type MonadStack[A] = StateT[IO, SearchLinkCriteria, A]

    val dummyLog = new MockLogMonadState[MonadStack, SearchLinkCriteria]
    val mockDb = new MockDbForGetLinks[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)

    val expectedResult = SearchLinkCriteria(uid, status, isInitiator)
    // any value is fine as it should be overwritten in the execution
    val initialState = SearchLinkCriteria(UserId("value_doesnt_matter"))
    service.getLinks(uid, status, isInitiator).run(initialState).map {
      case (criteriaSentToDb, _) => criteriaSentToDb must be_==(expectedResult)
    }.unsafeRunSync()
  }

  def getLink = {
    for {
      log <- Log.default
      dbCache: Map[LinkId, Link] = Map(dummyLinkId -> mika_add_eren)
      mockDb = new MockDbForGetLink(dbCache)
      service = LinkService.default(mockDb, clock, log)

      existLink <- service.getLink(dummyLinkId)
      nonExistLInk <- service.getLink(LinkId("non exist link"))
    } yield (existLink must beSome(mika_add_eren)) and (nonExistLInk must beNone)
  }.unsafeRunSync()
}

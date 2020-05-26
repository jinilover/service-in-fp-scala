package org.jinilover
package microservice
package link

import java.time.Clock

import cats.instances.list._
import cats.syntax.traverse._
import cats.syntax.flatMap._

import cats.effect.IO

import org.specs2.Specification
import org.specs2.specification.core.SpecStructure

import LinkTypes.{Link, LinkId, LinkStatus, SearchLinkCriteria}

import Mock._

class LinkServiceSpec extends Specification {
  lazy val clock = Clock.systemDefaultZone()

  val dummyLinkId = LinkId("value doesn't matter")

  override def is: SpecStructure =
    s2"""
        LinkService
          should not allow user link to himself $userAddToHimself
          should handle unique key violation from db $handleUniqueKeyViolation
          should pass correct arguments in acceptLink $acceptLink
          should handle the value from db.remove properly $removeLink
          should form the required search criteria in getLinks $getLinks
          should pass the linkId to db in getLink $getLink
    """

  def userAddToHimself = {
    val log = Log.default.unsafeRunSync()
    val mockDb = new DummyPersistence[IO]
    val service = LinkService.default[IO](mockDb, clock, log)

    service.addLink(eren, eren).unsafeRunSync() must
      throwAn[Error].like { case InputError(msg) =>
        msg must be_==("Both user ids are the same")
      }
  }

  def handleUniqueKeyViolation = {
    val log = Log.default.unsafeRunSync()
    val mockDb = new MockDbViolateUniqueKey(dummyLinkId)
    val service = LinkService.default(mockDb, clock, log)

    (service.addLink(mikasa, eren).unsafeRunSync() must be_==(dummyLinkId)) and
      (service.addLink(mikasa, eren).unsafeRunSync() must
        throwAn[Error].like { case InputError(msg) =>
          msg must be_==(s"Link between ${mikasa.unwrap} and ${eren.unwrap} already exists")
        })
  }

  def acceptLink =
    (
      for {
        log <- Log.default
        mockDb = new MockDbForUpdateLink
        service = LinkService.default(mockDb, clock, log)
        _ <- service.acceptLink(dummyLinkId)
      } yield
        (mockDb.linkId must be_==(dummyLinkId)) and
          (Math.abs(mockDb.confirmDate.getEpochSecond - clock.instant.getEpochSecond) must be ~(1L +/- 1L)) and
          (mockDb.status must be_==(LinkStatus.Accepted))
    ).unsafeRunSync()

  def removeLink =
    (
      for {
        log <- Log.default
        mockDb = new MockDbForRemoveLink
        service = LinkService.default(mockDb, clock, log)

        // run twice, where each time should return different messages
        msgs <- List.fill(2)(dummyLinkId).traverse(service.removeLink)
      } yield
        (msgs(0) must be_==(s"Linkid ${dummyLinkId.unwrap} removed successfully")) and
          (msgs(1) must be_==(s"No need to remove non-exist linkid ${dummyLinkId.unwrap}"))
    ).unsafeRunSync()

  def getLinks =
    (
      for {
        log <- Log.default
        mockDb = new MockDbForGetLinks(Nil)
        service = LinkService.default(mockDb, clock, log)

        _ <- service.getLinks(mikasa, Some(LinkStatus.Pending), Some(true))
        expectedSearchCriteria = SearchLinkCriteria(mikasa, Some(LinkStatus.Pending), Some(true))
      } yield mockDb.searchCriteria must be_==(expectedSearchCriteria)
    ).unsafeRunSync()

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

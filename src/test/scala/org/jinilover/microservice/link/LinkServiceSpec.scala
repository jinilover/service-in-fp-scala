package org.jinilover
package microservice
package link

import java.time.{Clock}

import org.specs2.Specification
import org.specs2.specification.core.SpecStructure

import LinkTypes._

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
    """

  def userAddToHimself = {
    val mockDb = new DummyPersistence
    val service = LinkService.default(mockDb, clock)

    service.addLink(eren, eren).unsafeRunSync() must
      throwAn[Error].like { case InputError(msg) =>
        msg must be_==("Both user ids are the same")
      }
  }

  def handleUniqueKeyViolation = {
    val service = LinkService.default(new MockDbViolateUniqueKey, clock)
    service.addLink(mikasa, eren).unsafeRunSync()

    service.addLink(mikasa, eren).unsafeRunSync() must
      throwAn[Error].like { case InputError(msg) =>
        msg must be_==(s"Link between ${mikasa.unwrap} and ${eren.unwrap} already exists")
      }
  }

  def acceptLink = {
    val mockDb = new MockDbForUpdateLink
    val service = LinkService.default(mockDb, clock)
    service.acceptLink(dummyLinkId).unsafeRunSync

    (mockDb.linkId must be_==(dummyLinkId)) and
    (Math.abs(mockDb.confirmDate.getEpochSecond - clock.instant.getEpochSecond) must be ~(1L +/- 1L)) and
    (mockDb.status must be_==(LinkStatus.Accepted))
  }

  def removeLink = {
    val mockDb = new MockDbForRemoveLink
    val service = LinkService.default(mockDb, clock)

    (service.removeLink(dummyLinkId).unsafeRunSync()
      must be_==(s"Linkid ${dummyLinkId.unwrap} removed successfully")) and
    (service.removeLink(dummyLinkId).unsafeRunSync()
      must be_==(s"No need to remove non-exist linkid ${dummyLinkId.unwrap}"))
  }

  def getLinks = {
    val mockDb = new MockDbForGetLinks(Nil)
    val service = LinkService.default(mockDb, clock)

    service.getLinks(mikasa, Some(LinkStatus.Pending), Some(true)).unsafeRunSync()
    val expectedSearchCriteria = SearchLinkCriteria(mikasa, Some(LinkStatus.Pending), Some(true))

    mockDb.searchCriteria must be_==(expectedSearchCriteria)
  }
}

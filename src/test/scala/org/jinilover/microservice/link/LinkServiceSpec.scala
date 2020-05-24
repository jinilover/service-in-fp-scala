package org.jinilover
package microservice
package link

import java.time.{Clock, Instant}

import cats.effect.IO

import org.specs2.Specification
import org.specs2.specification.core.SpecStructure

import LinkTypes._
import persistence.LinkPersistence

import MockData._

class DummyPersistence extends LinkPersistence[IO] {
  override def add(link: LinkTypes.Link): IO[LinkId] = ???

  override def update(linkId: LinkId, confirmDate: Instant, status: LinkStatus): IO[Unit] = ???

  override def get(id: LinkId): IO[Option[LinkTypes.Link]] = ???

  override def getLinks(srchCriteria: LinkTypes.SearchLinkCriteria): IO[List[LinkId]] = ???

  override def remove(id: LinkId): IO[Int] = ???
}

class LinkServiceSpec extends Specification {
  lazy val clock = Clock.systemDefaultZone()

  override def is: SpecStructure =
    s2"""
        LinkService
          should not allow user link to himself $userAddToHimself
          should handle unique key violation from db $handleUniqueKeyViolation

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
    class MockDb extends DummyPersistence {
      var linkSet = Set.empty[String]

      override def add(link: LinkTypes.Link): IO[LinkId] = {
        val uniqueKey = linkKey(link.initiatorId, link.targetId)
        if (linkSet contains uniqueKey)
          IO.raiseError(new RuntimeException("""violates unique constraint "unique_unique_key""""))
        else {
          linkSet += uniqueKey
          IO.pure(LinkId("dummy linkId"))
        }
      }
    }

    val service = LinkService.default(new MockDb, clock)
    service.addLink(mikasa, eren).unsafeRunSync()

    service.addLink(mikasa, eren).unsafeRunSync() must
      throwAn[Error].like { case InputError(msg) =>
        msg must be_==(s"Link between ${mikasa.unwrap} and ${eren.unwrap} already exists")
      }
  }
}

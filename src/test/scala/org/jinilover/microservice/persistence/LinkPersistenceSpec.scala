package org.jinilover.microservice.persistence

import java.time.Clock

import cats.effect.IO
import doobie.syntax.connectionio._
import doobie.util.ExecutionContexts
import doobie.util.update.Update0
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.{BeforeEach}
import org.jinilover.microservice.LinkStatus
import org.jinilover.microservice.LinkTypes.{Link, SearchLinkCriteria, UserId, linkKey}
import org.postgresql.util.PSQLException

class LinkPersistenceSpec extends Specification with BeforeEach {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val xa = Doobie.transactor
  val clock = Clock.systemDefaultZone()

  def createSchema: Unit = {
    //TODO replace `postgres` by config
    val sql = s"""
      DROP SCHEMA public CASCADE;
      CREATE SCHEMA public;
      GRANT ALL ON SCHEMA public TO postgres;
      GRANT ALL ON SCHEMA public TO public;
    """
    val dropAndCreate = for {
      _ <- Update0(sql, None).run.transact(xa)
      _ <- Migrations.default().migrate
    } yield ()
    dropAndCreate.unsafeRunSync()
  }

  override def before(): Unit =
    createSchema

  val linkPersistence = LinkPersistence.default(xa, clock)

  // sample user id
  val List(mikasa, eren, armin, annie, reiner, bert, levi) =
    List("mikasa", "eren", "armin", "annie", "reiner", "bert", "levi").map(UserId.apply)

  lazy val sampleLink = {
    Link(
        id = None
      , initiatorId = mikasa
      , targetId = eren
      , status = LinkStatus.Pending
      , creationDate = None
      , confirmDate = None
      , uniqueKey = linkKey(mikasa, eren)
    )
  }

  lazy val sampleCriteria =
    SearchLinkCriteria(
      userId = eren
      , linkStatus = None
      , isInitiator = None
    )


  override def is: SpecStructure =
    s2"""
      LinkPersistence
        should add 1 link and retrieve the link correctly $addLink
        ${step("cannot run in parallel due to accessing the same table `links`")}
        should raise error of unique key violation $violateUniqueKey
    """

  def addLink = {
    linkPersistence.add(sampleLink).unsafeRunSync()

    val linkIds = linkPersistence.getLinks(sampleCriteria).unsafeRunSync()
    linkIds.size must be_==(1)

    val linkFromDb = linkPersistence.get(linkIds(0)).unsafeRunSync()
    val tupledResult = for {
      v <- linkFromDb
      Link(_, initiatorId, targetId, status, _, _, uniqueKey) = v
    } yield (initiatorId, targetId, status, uniqueKey)
    tupledResult must be_==(Some(mikasa, eren, LinkStatus.Pending, "eren_mikasa"))

    linkFromDb.flatMap(_.id).nonEmpty must beTrue
    linkFromDb.flatMap(_.creationDate).nonEmpty must beTrue
    linkFromDb.flatMap(_.confirmDate).isEmpty must beTrue

  }

  def violateUniqueKey = {
    val link = sampleLink

    linkPersistence.add(link).unsafeRunSync()

    // this time should violate unique key constraint
    linkPersistence.add(link).unsafeRunSync() must
      throwAn[PSQLException].like { case e =>
        e.getMessage.toLowerCase must contain("""violates unique constraint "unique_unique_key"""")
      }
  }

}

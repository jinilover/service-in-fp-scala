package org.jinilover.microservice.persistence

import java.time.Clock

import cats.effect.IO
import doobie.syntax.connectionio._
import doobie.util.ExecutionContexts
import doobie.util.update.Update0
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.{BeforeAll, BeforeEach}
import org.jinilover.microservice.LinkStatus
import org.jinilover.microservice.LinkTypes.{Link, UserId, linkKey}
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

  override def is: SpecStructure =
    s2"""
      LinkPersistence
        should add links and retrieve the same links $addLink
        ${step("cannot run in parallel due to accessing the same table `links`")}
        should raise error of unique key violation $violateUniqueKey
    """

  def addLink = {
    val List(initiatorId, targetId) =
      List("mikasa", "eren").map(UserId.apply)

    val link = dummyLink.copy(
      initiatorId = initiatorId
    , targetId = targetId
    , uniqueKey = linkKey(initiatorId, targetId))

    linkPersistence.add(link).unsafeRunSync()


    true must beTrue
  }

  def violateUniqueKey = {
    val List(initiatorId, targetId) =
      List("mikasa", "eren").map(UserId.apply)

    val link = dummyLink.copy(
      initiatorId = initiatorId
      , targetId = targetId
      , uniqueKey = linkKey(initiatorId, targetId))

    linkPersistence.add(link).unsafeRunSync()

    // this time should violate unique key constraint
    linkPersistence.add(link).unsafeRunSync() must
      throwAn[PSQLException].like { case e =>
        e.getMessage.toLowerCase must contain("""violates unique constraint "unique_unique_key"""")
      }
  }

  lazy val dummyLink = {
    val uid = UserId("")
    Link(
      id = None
      , initiatorId = uid
      , targetId = uid
      , status = LinkStatus.Pending
      , creationDate = None
      , confirmDate = None
      , uniqueKey = ""
    )
  }

}

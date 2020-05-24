package org.jinilover.microservice.persistence

import java.time.Clock

import cats.instances.list._
import cats.syntax.traverse._

import cats.effect.IO

import doobie.syntax.connectionio._
import doobie.util.ExecutionContexts
import doobie.util.update.Update0

import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.BeforeEach

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

  val linkDb = LinkPersistence.default(xa, clock)

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
        .map { case (initiator, target) => Link(initiatorId = initiator, targetId = target) }

  lazy val simpleSearch =
    SearchLinkCriteria(
      userId = eren
      , linkStatus = None
      , isInitiator = None
    )


  override def is: SpecStructure = {
    val reasonOfStep = "cannot run in parallel due to accessing the same table `links`"

    s2"""
      LinkPersistence
        should add 1 link and retrieve the link correctly $addLink
        ${step{reasonOfStep}}
        should add links and retrieve the links accordingly $addLinks
        ${step(reasonOfStep)}
        should raise error of unique key violation $violateUniqueKey
    """
  }

  def addLink = {
    linkDb.add(mika_add_eren).unsafeRunSync()

    val linkIds = linkDb.getLinks(simpleSearch).unsafeRunSync()

    val linkFromDb = linkDb.get(linkIds(0)).unsafeRunSync()

    (linkIds.size must be_==(1)) and
      (linkFromDb.flatMap(_.id) must beSome) and
      (linkFromDb.map(_.initiatorId) must beSome(mikasa)) and
      (linkFromDb.map(_.targetId) must beSome(eren)) and
      (linkFromDb.flatMap(_.status) must beSome(LinkStatus.Pending)) and
      (linkFromDb.flatMap(_.uniqueKey) must beSome("eren_mikasa")) and
      (linkFromDb.flatMap(_.creationDate) must beSome) and
      (linkFromDb.flatMap(_.confirmDate) must beNone)
  }

  def addLinks = {
    List(
      mika_add_eren, reiner_add_eren, bert_add_eren, eren_add_armin, eren_add_annie,
      eren_add_levi, eren_add_erwin
    ).traverse(linkDb.add)
      .void
      .unsafeRunSync()

    val linkIds1 = linkDb.getLinks(simpleSearch).unsafeRunSync()
    linkIds1.size must be_==(7)

    val srchCriterias = List(
      simpleSearch
    , simpleSearch.copy(linkStatus = Some(LinkStatus.Accepted)) // search `Accepted` only
    , simpleSearch.copy(isInitiator = Some(true)) // the user is inititator
    , simpleSearch.copy(isInitiator = Some(false)) // the user target
    )

    // get the # of records searched for each query
    val linkSizes = srchCriterias
      .traverse(linkDb.getLinks)
      .unsafeRunSync()
      .map(_.size)

    linkSizes must be_==(List(7, 0, 4, 3))
  }

  //TODO leave for search query
//  val linkIds1 = linkDb.getLinks(sampleCriteria).unsafeRunSync()
//  linkIds1.size must be_==(7)
//
//  val searchPending = sampleCriteria.copy(linkStatus = Some(LinkStatus.Pending))
//
//  val searchAccepted = sampleCriteria.copy(linkStatus = Some(LinkStatus.Accepted))
//
//  val searchUserIsInititator = sampleCriteria.copy()


  def violateUniqueKey = {
    val link = mika_add_eren

    linkDb.add(link).unsafeRunSync()

    // this time should violate unique key constraint
    linkDb.add(link).unsafeRunSync() must
      throwAn[PSQLException].like { case e =>
        e.getMessage.toLowerCase must contain("""violates unique constraint "unique_unique_key"""")
      }
  }

}

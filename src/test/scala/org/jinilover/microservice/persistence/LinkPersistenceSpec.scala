package org.jinilover
package microservice
package persistence

import java.time.Clock

import cats.instances.list._
import cats.syntax.traverse._
import cats.syntax.flatMap._

import cats.effect.IO

import doobie.syntax.connectionio._
import doobie.util.ExecutionContexts
import doobie.util.update.Update0

import org.postgresql.util.PSQLException

import org.specs2.{ScalaCheck, Specification}
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.BeforeEach

import LinkTypes.{LinkStatus, UserId}
import config.ConfigLoader

import Mock._
import LinkTypeArbitraries._

class LinkPersistenceSpec extends Specification with ScalaCheck with BeforeEach {
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val dbConfig = ConfigLoader.default.load.map(_.db).unsafeRunSync()
  val xa = Doobie.transactor(dbConfig)
  val clock = Clock.systemDefaultZone()

  def createSchema: Unit = {
    val sql = s"""
      DROP SCHEMA public CASCADE;
      CREATE SCHEMA public;
      GRANT ALL ON SCHEMA public TO postgres;
      GRANT ALL ON SCHEMA public TO public;
    """
    val dropAndCreate = for {
      log <- Log.default
      _ <- Update0(sql, None).run.transact(xa)
      _ <- Migrations.default(log, dbConfig).migrate
    } yield ()
    dropAndCreate.unsafeRunSync()
  }

  override def before(): Unit =
    createSchema

  val persistence = LinkPersistence.default(xa)

  override def is: SpecStructure = {
    val reasonOfStep = "cannot run in parallel due to accessing the same table `links`"

    s2"""
      LinkPersistence
        should add 1 link and handle uniqueKey violation or retrieve the link correctly $addLink
        ${step(reasonOfStep)}
        should add links and retrieve the links accordingly $addLinks
        ${step{reasonOfStep}}
        should update 1 link and retrieve the link correctly $updateLink
        ${step{reasonOfStep}}
        should remove 1 link successfully $removeLink
        ${step{reasonOfStep}}
        should add/update links and retrieve the links accordingly $addAndUpdateLinks
    """
  }

  def addLink = prop { (uidPair: (UserId, UserId)) =>
    val (uid1, uid2) = uidPair

    val linkAlreadyExists = {
      for {
        find1 <- persistence.getByUniqueKey(uid1, uid2)
        find2 <- persistence.getByUniqueKey(uid2, uid1)
      } yield List(find1, find2).flatten
    }.map(_.nonEmpty)
      .unsafeRunSync()

    val addIO = persistence.add(createNewLink(uid1, uid2))

    if (linkAlreadyExists)
      addIO.unsafeRunSync() must throwAn[PSQLException].like { case e =>
        e.getMessage.toLowerCase must contain("""violates unique constraint "unique_unique_key"""")
      }
    else {
      val linkId = addIO.unsafeRunSync()
      val link = persistence.get(linkId).unsafeRunSync()
      val initiatorId = link.map(_.initiatorId)
      val targetId = link.map(_.targetId)
      val expectedUniqueKey = {
        val List(s1, s2) = List(uid1, uid2).map(_.unwrap)
        if (s1 < s2) s"${s1}_${s2}" else s"${s2}_${s1}"
      }

      (link.flatMap(_.id) must beSome(linkId)) and
      (
        ((initiatorId must beSome(uid1)) and (targetId must beSome(uid2))) or
        ((initiatorId must beSome(uid2)) and (targetId must beSome(uid1)))
      ) and
      (link.map(_.status) must beSome(LinkStatus.Pending)) and
      (link.flatMap(_.uniqueKey) must beSome(expectedUniqueKey)) and
      (link.map(_.creationDate) must beSome) and
      (link.flatMap(_.confirmDate) must beNone)
    }
  }.setArbitrary(unequalUserIdsPairArbitrary)

  def addLinks = {
    List(
      mika_add_eren, reiner_add_eren, bert_add_eren,
      eren_add_armin, eren_add_annie, eren_add_levi, eren_add_erwin
    ).traverse(persistence.add)
      .void
      .unsafeRunSync()

    // get the # of records searched for each query
    val linkSizes = possibleErenSearchCriterias
      .traverse(persistence.getLinks)
      .unsafeRunSync()
      .map(_.size)

    linkSizes must be_==(List(7, 0, 7, 4, 3, 0, 4, 0, 3))
  }

  // similar to `addLink` but it also update the status/confirmDate afterwards
  def updateLink = {
    val linkId = persistence.add(mika_add_eren).unsafeRunSync()

    val confirmDate = clock.instant()
    persistence.update(linkId, confirmDate, LinkStatus.Accepted).unsafeRunSync()

    val linkFromDb = persistence.get(linkId).unsafeRunSync()

    (linkFromDb.flatMap(_.id) must beSome(linkId)) and
    (linkFromDb.map(_.initiatorId) must beSome(mikasa)) and
    (linkFromDb.map(_.targetId) must beSome(eren)) and
    (linkFromDb.map(_.status) must beSome(LinkStatus.Accepted)) and // status must be `Accept` due to update
    (linkFromDb.flatMap(_.uniqueKey) must beSome("eren_mikasa")) and
    (linkFromDb.map(_.creationDate) must beSome(mika_add_eren.creationDate)) and
    (linkFromDb.flatMap(_.confirmDate) must beSome(confirmDate)) // confirmDate nonEmpty due to update
  }

  // similar to `addLinks` but this time the query result will be slightly different
  // as some links are updated the status
  def addAndUpdateLinks = {
    val linkIds =
      List(
        mika_add_eren, reiner_add_eren, bert_add_eren,
        eren_add_armin, eren_add_annie, eren_add_levi, eren_add_erwin
      ).traverse(persistence.add)
        .unsafeRunSync()

    // update the first 2 linkIds to accepted
    val confirmDate = clock.instant()
    List(linkIds(0), linkIds(1))
      .traverse(persistence.update(_, confirmDate, LinkStatus.Accepted))
      .void
      .unsafeRunSync()

    // get the # of records searched for each query
    val linkSizes = possibleErenSearchCriterias
      .traverse(persistence.getLinks)
      .unsafeRunSync()
      .map(_.size)

    linkSizes must be_==(List(7, 2, 5, 4, 3, 0, 4, 2, 1))
  }

  def removeLink = {
    val linkId = persistence.add(mika_add_eren).unsafeRunSync()

    val count1 = persistence.remove(linkId).unsafeRunSync() // 1
    val count2 = persistence.remove(linkId).unsafeRunSync() // 0

    val linkIdsFromDb = persistence.getLinks(erenSearchCriteria).unsafeRunSync()

    (count1 must be_==(1)) and
    (count2 must be_==(0)) and
    (linkIdsFromDb must beEmpty)
  }


}

package org.jinilover
package microservice
package persistence

import java.time.Clock

import cats.instances.list._
import cats.syntax.traverse._

import cats.effect.IO

import doobie.syntax.connectionio._
import doobie.util.ExecutionContexts
import doobie.util.update.Update0

import org.postgresql.util.PSQLException

import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.BeforeEach

import MockData._

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

  val persistence = LinkPersistence.default(xa)

  override def is: SpecStructure = {
    val reasonOfStep = "cannot run in parallel due to accessing the same table `links`"

    s2"""
      LinkPersistence
        should add 1 link and retrieve the link correctly $addLink
        ${step(reasonOfStep)}
        should raise error of unique key violation $violateUniqueKey
        ${step{reasonOfStep}}
        should add links and retrieve the links accordingly $addLinks
        ${step{reasonOfStep}}
        should update 1 link and retrieve the link correctly $updateLink
        ${step{reasonOfStep}}
        should remove 1 link successfully $removeLink
        ${step{reasonOfStep}}
        should add/update links and retrieve the links accordingly $addUpdateLinks
    """
  }

  def addLink = {
    val linkId = persistence.add(mika_add_eren).unsafeRunSync()

    val linkIdsFromDb = persistence.getLinks(simpleSearch).unsafeRunSync()

    val linkFromDb = persistence.get(linkId).unsafeRunSync()

    (linkIdsFromDb must be_==(List(linkId))) and
      (linkFromDb.flatMap(_.id) must beSome) and
      (linkFromDb.map(_.initiatorId) must beSome(mikasa)) and
      (linkFromDb.map(_.targetId) must beSome(eren)) and
      (linkFromDb.map(_.status) must beSome(LinkStatus.Pending)) and
      (linkFromDb.flatMap(_.uniqueKey) must beSome("eren_mikasa")) and
      (linkFromDb.map(_.creationDate) must beSome(mika_add_eren.creationDate)) and
      (linkFromDb.flatMap(_.confirmDate) must beNone)
  }

  def violateUniqueKey = {
    persistence.add(mika_add_eren).unsafeRunSync()

    lazy val expectedException =
      throwAn[PSQLException].like { case e =>
        e.getMessage.toLowerCase must contain("""violates unique constraint "unique_unique_key"""")
      }

    // it should violate unique key constraint in adding the same link
    // or even the user ids swapped
    val eren_add_mika = mika_add_eren.copy(initiatorId = eren, targetId = mikasa)

    (persistence.add(eren_add_mika).unsafeRunSync() must expectedException) and
      (persistence.add(mika_add_eren).unsafeRunSync() must expectedException)
  }

  def addLinks = {
    List(
      mika_add_eren, reiner_add_eren, bert_add_eren, eren_add_armin, eren_add_annie,
      eren_add_levi, eren_add_erwin
    ).traverse(persistence.add)
      .void
      .unsafeRunSync()

    val srchCriterias = List(
      simpleSearch
    , simpleSearch.copy(linkStatus = Some(LinkStatus.Accepted)) // search `Accepted` only
    , simpleSearch.copy(isInitiator = Some(true)) // the user is inititator
    , simpleSearch.copy(isInitiator = Some(false)) // the user target
    )

    // get the # of records searched for each query
    val linkSizes = srchCriterias
      .traverse(persistence.getLinks)
      .unsafeRunSync()
      .map(_.size)

    linkSizes must be_==(List(7, 0, 4, 3))
  }

  // similar test and check as `addLink` but it update the status/confirmDate afterwards
  def updateLink = {
    val linkId = persistence.add(mika_add_eren).unsafeRunSync()

    val confirmDate = clock.instant()
    persistence.update(linkId, confirmDate, LinkStatus.Accepted).unsafeRunSync()

    val linkIdsFromDb = persistence.getLinks(simpleSearch).unsafeRunSync()

    val linkFromDb = persistence.get(linkIdsFromDb(0)).unsafeRunSync()

    (linkIdsFromDb.size must be_==(1)) and
      (linkFromDb.flatMap(_.id) must beSome) and
      (linkFromDb.map(_.initiatorId) must beSome(mikasa)) and
      (linkFromDb.map(_.targetId) must beSome(eren)) and
      (linkFromDb.map(_.status) must beSome(LinkStatus.Accepted)) and // status must be `Accept` due to update
      (linkFromDb.flatMap(_.uniqueKey) must beSome("eren_mikasa")) and
      (linkFromDb.map(_.creationDate) must beSome(mika_add_eren.creationDate)) and
      (linkFromDb.flatMap(_.confirmDate) must beSome(confirmDate)) // confirmDate nonEmpty due to update
  }

  def addUpdateLinks = {
    val linkIds =
      List(
        mika_add_eren, reiner_add_eren, bert_add_eren, eren_add_armin, eren_add_annie,
        eren_add_levi, eren_add_erwin
      ).traverse(persistence.add)
        .unsafeRunSync()

    // update the first 2 linkIds to accepted
    val confirmDate = clock.instant()
    List(linkIds(0), linkIds(1))
      .traverse(persistence.update(_, confirmDate, LinkStatus.Accepted))
      .void
      .unsafeRunSync()

    val srchCriterias = List(
        simpleSearch
      , simpleSearch.copy(linkStatus = Some(LinkStatus.Accepted)) // search `Accepted` only
      , simpleSearch.copy(linkStatus = Some(LinkStatus.Pending)) // search `Pending` only
      , simpleSearch.copy(isInitiator = Some(true)) // the user is initiator only
      , simpleSearch.copy(isInitiator = Some(false)) // the user target only
      , simpleSearch.copy(isInitiator = Some(true), linkStatus = Some(LinkStatus.Accepted))
      , simpleSearch.copy(isInitiator = Some(true), linkStatus = Some(LinkStatus.Pending))
      , simpleSearch.copy(isInitiator = Some(false), linkStatus = Some(LinkStatus.Accepted))
      , simpleSearch.copy(isInitiator = Some(false), linkStatus = Some(LinkStatus.Pending))
    )

    // get the # of records searched for each query
    val linkSizes = srchCriterias
      .traverse(persistence.getLinks)
      .unsafeRunSync()
      .map(_.size)

    linkSizes must be_==(List(7, 2, 5, 4, 3, 0, 4, 2, 1))
  }

  def removeLink = {
    val linkId = persistence.add(mika_add_eren).unsafeRunSync()

    val count1 = persistence.remove(linkId).unsafeRunSync() // 1
    val count2 = persistence.remove(linkId).unsafeRunSync() // 0

    val linkIdsFromDb = persistence.getLinks(simpleSearch).unsafeRunSync()

    (count1 must be_==(1)) and
      (count2 must be_==(0)) and
      (linkIdsFromDb must beEmpty)
  }


}

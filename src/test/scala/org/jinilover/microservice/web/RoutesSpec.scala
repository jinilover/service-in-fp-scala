package org.jinilover
package microservice
package web

import java.time.Clock

import cats.instances.list._
import cats.instances.either._
import cats.syntax.flatMap._
import cats.syntax.traverse._

import cats.effect.IO

import fs2.Stream

import io.circe.parser._
import io.circe.Error

import org.http4s._
import org.http4s.implicits._
import org.specs2.Specification

import buildInfo.BuildInfo

import ops.OpsService
import OpsTypes.VersionInfo
import link.LinkService
import LinkTypes.{Link, LinkId}
import persistence.LinkPersistence

import Mock._

class RoutesSpec extends Specification {
  lazy val clock = Clock.systemDefaultZone()

  override def is =
    s2"""
      Routes must
        Get   / $welcomeMsgOk
        Get   /version_info $versionInfoOk
        POST  /users/userId/links return bad request when user attempts to add himself $userAddToHimself
        POST  /users/userId/links return bad request when user adds the same link twice $addLink
        GET   /users/userId/links extract the required query parameter $getLinksWithQueryParams
        GET   /links/linkId for existing link $getExistingLink
    """
//  TODO put back afterwards

  //  GET /users/userId/links query parameters successfully $getUserIdLinksWithQueryParams
  //  POST /users/userId/links successfully $addLink

  def welcomeMsgOk = {
    val routes = (createRoutes compose createLinkService)(new DummyPersistence)

    val expected = List(""""Welcome to REST servce in functional Scala!"""")
    val req = Request[IO](Method.GET, uri"/")
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    (res.status must be_==(Status.Ok)) and
      (bodyText must be_==(expected))
  }

  def versionInfoOk = {
    val routes = (createRoutes compose createLinkService)(new DummyPersistence)

    val expected = List(Right(VersionInfo(
        name = BuildInfo.name
      , version = BuildInfo.version
      , scalaVersion = BuildInfo.scalaVersion
      , sbtVersion = BuildInfo.sbtVersion
      , gitCommitHash = BuildInfo.gitCommitHash
      , gitCommitMessage = BuildInfo.gitCommitMessage
      , gitCommitDate = BuildInfo.gitCommitDate
      , gitCurrentBranch = BuildInfo.gitCurrentBranch
      )))
    val req = Request[IO](Method.GET, uri"/version_info")
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    (res.status must be_==(Status.Ok)) and
      (bodyText.map{s => parse(s).flatMap(_.as[VersionInfo])} must be_==(expected))
  }

  def userAddToHimself = {
    val routes = (createRoutes compose createLinkService)(new DummyPersistence)

    val expected = List(""""Both user ids are the same"""")
    val req =
      Request[IO](Method.POST,
        uri"/users/eren/links",
        body = createEntityBody(""""eren"""")
      )
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    (res.status must be_==(Status.BadRequest)) and
      (bodyText must be_==(expected))
  }

  def addLink = {
    val routes = (createRoutes compose createLinkService)(new MockDbViolateUniqueKey(dummyLinkId))

    val req =
      Request[IO](
        Method.POST,
        uri"/users/eren/links",
        body = createEntityBody(""""mikasa"""")
      )
    val okExpected = List(s""""${dummyLinkId.unwrap}"""")
    val okRes = routes.routes.run(req).unsafeRunSync()
    val okMsg = getBodyText(okRes)

    val badExpected = List(s""""Link between eren and mikasa already exists"""")
    val badRes = routes.routes.run(req).unsafeRunSync()
    val badMsg = getBodyText(badRes)

    (okRes.status must be_==(Status.Ok)) and
      (okMsg must be_==(okExpected)) and
      (badRes.status must be_==(Status.BadRequest)) and
      (badMsg must be_==(badExpected))
  }

  def getLinksWithQueryParams = {
    val createReq: Uri => Request[IO] = Request[IO](Method.GET, _)

    val mockDb = new MockDbForGetLinks(Nil)
    val linkService = LinkService.default(mockDb, clock)
    val routes = createRoutes(linkService)

    val reqs = List(
      createReq(uri"/users/eren/links")
    , createReq(uri"/users/eren/links?status=Pending")
    , createReq(uri"/users/eren/links?status=Accepted")
    , createReq(uri"/users/eren/links?is_initiator=true")
    , createReq(uri"/users/eren/links?is_initiator=false")
    , createReq(uri"/users/eren/links?is_initiator=false&status=Accepted")
    , createReq(uri"/users/eren/links?status=Pending&is_initiator=true")
    )

    val srchCriterias =
      reqs.traverse { req =>
        routes.routes.run(req) >> IO(mockDb.searchCriteria)
      }.unsafeRunSync()

    (srchCriterias(0) must be_==(erenSearchCriteria)) and
      (srchCriterias(1) must be_==(erenSearchCriteria.copy(linkStatus = Some(LinkStatus.Pending)))) and
      (srchCriterias(2) must be_==(erenSearchCriteria.copy(linkStatus = Some(LinkStatus.Accepted)))) and
      (srchCriterias(3) must be_==(erenSearchCriteria.copy(isInitiator = Some(true)))) and
      (srchCriterias(4) must be_==(erenSearchCriteria.copy(isInitiator = Some(false)))) and
      (srchCriterias(5) must be_==(erenSearchCriteria.copy(isInitiator = Some(false), linkStatus = Some(LinkStatus.Accepted)))) and
      (srchCriterias(6) must be_==(erenSearchCriteria.copy(isInitiator = Some(true), linkStatus = Some(LinkStatus.Pending))))
  }

  def getExistingLink = {
    val dbCache: Map[LinkId, Link] = Map(LinkId("exist_linkid") -> mika_add_eren)
    val routes = (createRoutes compose createLinkService)(new MockDbForGetLink(dbCache))
    val reqs =
      List(
        Request[IO](Method.GET, uri"/links/exist_linkid")
      )

    type DecodeResult[A] = Either[Error, A]

    val decodeResults: List[DecodeResult[List[Link]]] =
      reqs
        .traverse { req =>
          for {
            res <- routes.routes.run(req)
            multiStrs <- res.bodyAsText.compile.toList
          } yield multiStrs
                  .traverse{ parse(_).flatMap(_.as[List[Link]]) }
                  .map(_.flatten)
        }
        .unsafeRunSync()

    decodeResults(0) must be_==(Right(List(mika_add_eren)))
  }

  private def createEntityBody(s: String): EntityBody[IO] =
    Stream.emits(s.getBytes).evalMap(x => IO(x))

//  private def execReqForBody(req: Request[IO]): List[String] =
//    streamToStrings(routes.routes.run(req).unsafeRunSync.bodyAsText)

//  private def streamToStrings(stream: Stream[IO, String]): List[String] =
//    stream.compile.toList.unsafeRunSync()

  private def getBodyText(res: Response[IO]): List[String] =
    res.bodyAsText.compile.toList.unsafeRunSync()

  private val createLinkService: LinkPersistence[IO] => LinkService[IO] =
    LinkService.default(_, clock)

  private val createRoutes: LinkService[IO] => Routes[IO] =
    Routes.default[IO](OpsService.default, _)
}

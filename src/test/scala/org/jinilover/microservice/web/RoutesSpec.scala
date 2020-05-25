package org.jinilover
package microservice
package web

import java.time.Clock

import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.traverse._

import cats.effect.IO

import fs2.Stream

import io.circe.parser._

import org.http4s._
import org.http4s.implicits._
import org.specs2.Specification

import org.jinilover.microservice.ops.OpsService
import org.jinilover.microservice.OpsTypes.VersionInfo
import buildInfo.BuildInfo
import Mock._
import link.LinkService

class RoutesSpec extends Specification {
  lazy val clock = Clock.systemDefaultZone()

  override def is =
    s2"""
      Routes must
        Get / $welcomeMsgOk
        Get /version_info $versionInfoOk
        POST /users/userId/links return bad request when user attempts to add himself $userAddToHimself
        POST /users/userId/links return bad request when user adds the same link twice $addLink
        GET /users/userId/links extract the required query parameter $getLinksWithQueryParams
    """
//  TODO put back afterwards

  //  GET /users/userId/links query parameters successfully $getUserIdLinksWithQueryParams
  //  POST /users/userId/links successfully $addLink

  def welcomeMsgOk = {
    val mockDb = new DummyPersistence
    val linkService = LinkService.default(mockDb, clock)
    val routes = createRoutes(linkService)

    val expected = List(""""Welcome to REST servce in functional Scala!"""")
    val req = Request[IO](Method.GET, uri"/")
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    (res.status must be_==(Status.Ok)) and
      (bodyText must be_==(expected))
  }

  def versionInfoOk = {
    val mockDb = new DummyPersistence
    val linkService = LinkService.default(mockDb, clock)
    val routes = createRoutes(linkService)

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
    val mockDb = new DummyPersistence
    val linkService = LinkService.default(mockDb, clock)
    val routes = createRoutes(linkService)

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
    val mockDb = new MockDbViolateUniqueKey(dummyLinkId)
    val linkService = LinkService.default(mockDb, clock)
    val routes = createRoutes(linkService)

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

    srchCriterias(0) must be_==(erenSearchCriteria)



  }


  //  def getUserIdLinksWithQueryParams = {
//    val expected = List(""""Get all links of eren for Pending"""")
//    val req = Request[IO](Method.GET, uri"/users/eren/links?status=Pending")
//    val result = execReqForBody(req)
//
//    result must be_==(expected)
//
//    val req2 = Request[IO](Method.GET, uri"/users/eren/links?status=accepted")
//    val result2 = execReqForBody(req2)
//
//    result2 must be_==(List(""""Get all links of eren for Accepted""""))
//
//    val req3 = Request[IO](Method.GET, uri"/users/eren/links")
//    val result3 = execReqForBody(req3)
//
//    result3 must be_==(List(""""Get all links of eren """"))
//
//  }
//


  private def createEntityBody(s: String): EntityBody[IO] =
    Stream.emits(s.getBytes).evalMap(x => IO(x))

//  private def execReqForBody(req: Request[IO]): List[String] =
//    streamToStrings(routes.routes.run(req).unsafeRunSync.bodyAsText)

  private def streamToStrings(stream: Stream[IO, String]): List[String] =
    stream.compile.toList.unsafeRunSync()

  private def getBodyText(res: Response[IO]): List[String] =
    res.bodyAsText.compile.toList.unsafeRunSync()

  private def createRoutes(linkService: LinkService[IO]): Routes[IO] =
    Routes.default[IO](OpsService.default, linkService)
}

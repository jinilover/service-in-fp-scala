package org.jinilover
package microservice
package web

import java.time.Clock

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
        POST /users/userId/links when user attempts to add himself $userAddToHimself
        POST /users/userId/links when user adds the same link twice $addLink
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
    val result = routes.routes.run(req).unsafeRunSync()
      .bodyAsText.compile.toList.unsafeRunSync()

    result must be_==(expected)
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
    val strings = routes.routes.run(req).unsafeRunSync()
      .bodyAsText.compile.toList.unsafeRunSync()
    val result = strings.map { s =>
      parse(s).flatMap(_.as[VersionInfo])
    }

    result must be_==(expected)
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
    val msg = res.bodyAsText.compile.toList.unsafeRunSync()

    (res.status must be_==(Status.BadRequest)) and
      (msg must be_==(expected))
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
    val okMsg = okRes.bodyAsText.compile.toList.unsafeRunSync()

    val badExpected = List(s""""Link between eren and mikasa already exists"""")
    val badRes = routes.routes.run(req).unsafeRunSync()
    val badMsg = badRes.bodyAsText.compile.toList.unsafeRunSync()

    (okRes.status must be_==(Status.Ok)) and
      (okMsg must be_==(okExpected)) and
      (badRes.status must be_==(Status.BadRequest)) and
      (badMsg must be_==(badExpected))
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

  private def createRoutes(linkService: LinkService[IO]): Routes[IO] =
    Routes.default[IO](OpsService.default, linkService)
}

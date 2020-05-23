package org.jinilover.microservice.web

import cats.effect.IO

import fs2.Stream

import io.circe.parser._

import org.http4s._
import org.http4s.implicits._

import org.specs2.Specification

import org.jinilover.microservice.ops.OpsService
import org.jinilover.microservice.OpsTypes.VersionInfo
import buildInfo.BuildInfo
import org.jinilover.microservice.link.LinkService

class RoutesSpec extends Specification {
  def is =
    s2"""
      Routes must
        return welcome message $welcomeMsgOk
        return correct version info $versionInfoOk
        GET /users/userId/links query parameters successfully $getUserIdLinksWithQueryParams
        POST /users/userId/links when userId and targetId same $addLinkSameIds
    """
//  TODO revert afterwards
//  POST /users/userId/links successfully $addLink

  val routes = Routes.default[IO](OpsService.default, LinkService.default[IO])

  def welcomeMsgOk = {
    val expected = List(""""Welcome to REST servce in functional Scala!"""")
    val req = Request[IO](Method.GET, uri"/")
    val result = execReqForBody(req)

    result must be_==(expected)
  }

  def versionInfoOk = {
    val expected = VersionInfo(
        name = BuildInfo.name
      , version = BuildInfo.version
      , scalaVersion = BuildInfo.scalaVersion
      , sbtVersion = BuildInfo.sbtVersion
      , gitCommitHash = BuildInfo.gitCommitHash
      , gitCommitMessage = BuildInfo.gitCommitMessage
      , gitCommitDate = BuildInfo.gitCommitDate
      , gitCurrentBranch = BuildInfo.gitCurrentBranch
      )

    val req = Request[IO](Method.GET, uri"/version_info")
    val strings = execReqForBody(req)
    val result = strings.map { s =>
      parse(s).flatMap(_.as[VersionInfo])
    }

    (result.size must be_==(1)) and (result(0) must be_==(Right(expected)))
  }

  def getUserIdLinksWithQueryParams = {
    val expected = List(""""Get all links of eren for Pending"""")
    val req = Request[IO](Method.GET, uri"/users/eren/links?status=Pending")
    val result = execReqForBody(req)

    result must be_==(expected)

    val req2 = Request[IO](Method.GET, uri"/users/eren/links?status=accepted")
    val result2 = execReqForBody(req2)

    result2 must be_==(List(""""Get all links of eren for Accepted""""))

    val req3 = Request[IO](Method.GET, uri"/users/eren/links")
    val result3 = execReqForBody(req3)

    result3 must be_==(List(""""Get all links of eren """"))

  }

  def addLink = {
    val expected = List(""""eren_mikasa"""")
    val req =
      Request[IO](
        Method.POST,
        uri"/users/eren/links",
        body = createEntityBody("mikasa")
      )
    val result = execReqForBody(req)

    result must be_==(expected)
  }

  def addLinkSameIds = {
    val req =
      Request[IO](
        Method.POST,
        uri"/users/eren/links",
        body = createEntityBody("eren")
      )
    val response = routes.routes.run(req).unsafeRunSync

    response.status must be_==(Status.BadRequest)
    val bodyBytes = response.body.compile.toList.unsafeRunSync.toArray
    bodyBytes must be_==(""""Both user ids are the same"""".getBytes)
  }


  private def createEntityBody(s: String): EntityBody[IO] =
    Stream.emits(s.getBytes).evalMap(x => IO(x))

  private def execReqForBody(req: Request[IO]): List[String] =
    streamToStrings(routes.routes.run(req).unsafeRunSync.bodyAsText)

  private def streamToStrings(stream: Stream[IO, String]): List[String] =
    stream.compile.toList.unsafeRunSync()
}

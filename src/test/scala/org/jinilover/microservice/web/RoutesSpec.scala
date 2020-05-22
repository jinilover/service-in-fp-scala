package org.jinilover.microservice.web

import cats.effect.IO

import io.circe.parser._

import org.http4s._
import org.http4s.implicits._


import org.specs2.Specification

import org.jinilover.microservice.ops.OpsService
import org.jinilover.microservice.OpsTypes.VersionInfo
import org.jinilover.microservice.web._
import buildInfo.BuildInfo

class RoutesSpec extends Specification {
  def is =
    s2"""
      Routes must
        return welcome message $welcomeMsgOk
        return correct version info $versionInfoOk
    """

  val routes = Routes.default[IO](OpsService.default)

  def welcomeMsgOk = {
    val expected = List(""""Welcome to REST servce in functional Scala!"""")
    val req = Request[IO](Method.GET, uri"/")
    val result = routes.routes.run(req).unsafeRunSync.bodyAsText.compile.toList.unsafeRunSync()

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
    val strings = routes.routes.run(req).unsafeRunSync.bodyAsText.compile.toList.unsafeRunSync()
    val result = strings.map { s =>
      parse(s).flatMap(_.as[VersionInfo])
    }

    (result.size must be_==(1)) and (result(0) must be_==(Right(expected)))
  }
}

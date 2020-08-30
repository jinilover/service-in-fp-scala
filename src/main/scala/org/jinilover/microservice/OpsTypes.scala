package org.jinilover
package microservice

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

object OpsTypes {
  case class VersionInfo(
    name: String,
    version: String,
    scalaVersion: String,
    sbtVersion: String,
    gitCommitHash: String,
    gitCommitMessage: String,
    gitCommitDate: String,
    gitCurrentBranch: String
  )

  implicit val versionInfoEncoder: Encoder[VersionInfo] = deriveEncoder

  implicit val versionInfoDecoder: Decoder[VersionInfo] = deriveDecoder
}

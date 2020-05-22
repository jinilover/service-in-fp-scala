package org.jinilover.microservice

import io.circe.generic.semiauto._
import io.circe.{Encoder, Decoder}

object OpsTypes {
  case class VersionInfo(name: String,
                         version: String,
                         scalaVersion: String,
                         sbtVersion: String,
                         gitCommitHash: String,
                         gitCommitMessage: String,
                         gitCommitDate: String,
                         gitCurrentBranch: String)

  implicit val versionInfoEncoder: Encoder[VersionInfo] = deriveEncoder

  implicit val versionInfoDecoder: Decoder[VersionInfo] = deriveDecoder
}

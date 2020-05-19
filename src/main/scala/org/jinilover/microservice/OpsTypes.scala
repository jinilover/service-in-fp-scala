package org.jinilover.microservice

import io.circe.generic.semiauto._
import io.circe.Encoder

object OpsTypes {
  case class VersionInfo(name: String,
                         version: String,
                         scalaVersion: String,
                         sbtVersion: String,
                         gitCommitHash: String,
                         gitCommitMessage: String,
                         gitCommitDate: String,
                         gitCurrentBranch: String)

  object VersionInfo {
    implicit val verInfoEncoder: Encoder[VersionInfo] = deriveEncoder
  }
}

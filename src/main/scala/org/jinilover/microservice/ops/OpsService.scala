package org.jinilover
package microservice
package ops

import OpsTypes.VersionInfo
import buildInfo.BuildInfo

trait OpsService {
  def welcomeMsg(): String

  def versionInfo(): VersionInfo
}

object OpsService {
  def default: OpsService =
    new OpsService {
      override def welcomeMsg(): String =
        "Welcome to REST servce in functional Scala!"

      override def versionInfo(): VersionInfo =
        VersionInfo(
          name = BuildInfo.name,
          version = BuildInfo.version,
          scalaVersion = BuildInfo.scalaVersion,
          sbtVersion = BuildInfo.sbtVersion,
          gitCommitHash = BuildInfo.gitCommitHash,
          gitCommitMessage = BuildInfo.gitCommitMessage,
          gitCommitDate = BuildInfo.gitCommitDate,
          gitCurrentBranch = BuildInfo.gitCurrentBranch
        )
    }

}


name := "service-in-fp-scala"

version := "0.1"

scalaVersion := "2.12.7"

organization := "org.jinilover"

libraryDependencies ++= Dependencies.compile ++ Dependencies.test

addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3")
addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")

configs(IntegrationTest)
Defaults.itSettings

dependencyClasspath in IntegrationTest := (dependencyClasspath in IntegrationTest).value ++ (exportedProducts in Test).value

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
  "-Ypartial-unification"
)

// sbt-buildinfo for generating BuildInfo under buildInfo folder
enablePlugins(BuildInfoPlugin)
buildInfoPackage := "buildInfo"

// sbt-git is also needed to add git details to BuildInfo
val gitCommitHash = SettingKey[String]("gitCommitHash")
gitCommitHash := git.gitHeadCommit.value.getOrElse("No commit yet")

val gitCommitMessage = SettingKey[String]("gitCommitMessage")
gitCommitMessage := git.gitHeadMessage.value.getOrElse("No commit message")

val gitCommitDate = SettingKey[String]("gitCommitDate")
gitCommitDate := git.gitHeadCommitDate.value.getOrElse("No commit yet")

val gitCurrentBranch = SettingKey[String]("gitCurrentBranch")
gitCurrentBranch := git.gitCurrentBranch.value

buildInfoKeys ++= Seq(
  gitCommitHash,
  gitCommitMessage,
  gitCommitDate,
  gitCurrentBranch
)

enablePlugins(JavaAppPackaging)

// building a docker image
enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._

version in Docker := version.value.trim
dockerUpdateLatest in Docker := true
packageName in Docker := s"jinilover/${name.value}"
dockerBuildOptions ++= List("-t", dockerAlias.value.versioned)

val entryScript = "docker-entrypoint.sh"

dockerCommands := Seq(
  Cmd("FROM", "openjdk:11.0.4-jre-slim"),
  Cmd("MAINTAINER", "jinilover <columbawong@gmail.com>"),
  Cmd("WORKDIR", "/opt"),
  Cmd("LABEL", "version=\"" + version.value.trim + "\""),
  Cmd("ADD", "./opt/docker", "/opt"),
  Cmd("RUN", s"mv /opt/${entryScript} /opt/bin/${entryScript}"),
  Cmd("RUN", s"chmod +x /opt/bin/${entryScript}"),
  Cmd("CMD", s"/opt/bin/${entryScript} /opt/bin/${name.value}")
)




name := "serviceInFpScala"

version := "0.1"

scalaVersion := "2.12.7"

lazy val root = (project in file("."))
  .settings(
    organization := "org.jinilover",
    name := "microservice",
    libraryDependencies ++= Dependencies.compile ++ Dependencies.test,
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings",
  "-Ypartial-unification"
)

// sbt-buildinfo for generating BuildInfo details
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
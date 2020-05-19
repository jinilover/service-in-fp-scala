name := "serviceInFpScala"

version := "0.1"

scalaVersion := "2.12.7"

lazy val root = (project in file("."))
  .settings(
    organization := "org.jinilover",
    name := "microservice",
    libraryDependencies ++= Dependencies.compile,
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

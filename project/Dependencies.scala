import sbt._

object Dependencies {
  val Http4sVersion = "0.21.3"
  val CirceVersion = "0.13.0"
  val Specs2Version = "4.9.3"
  val LogbackVersion = "1.2.3"

  lazy val compile = Seq(
    "org.http4s"      %% "http4s-blaze-server" % Http4sVersion
  , "org.http4s"      %% "http4s-blaze-client" % Http4sVersion
  , "org.http4s"      %% "http4s-circe"        % Http4sVersion
  , "org.http4s"      %% "http4s-dsl"          % Http4sVersion
  , "io.circe"        %% "circe-generic"       % CirceVersion
  , "ch.qos.logback"  %  "logback-classic"     % LogbackVersion
  , "org.specs2"      %% "specs2-core"         % Specs2Version % "test"
  )
}

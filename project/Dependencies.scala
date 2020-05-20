import sbt._

object Dependencies {
  val doobieV = "0.8.8"

  lazy val compile = Seq(
    dep("org.http4s", "0.21.3",
      "http4s-blaze-server",
      "http4s-blaze-client",
      "http4s-circe",
      "http4s-dsl"),
    dep("io.circe", "0.13.0", "circe-generic"),
    dep("org.tpolecat", doobieV,
      "doobie-core",
      "doobie-postgres"),
    dep("io.monix", "3.2.1", "monix"),
    Seq("ch.qos.logback"  % "logback-classic" % "1.2.3"),
    Seq("org.flywaydb"    % "flyway-core"     % "5.0.7")
  ).flatten

  lazy val test = Seq(
    testDep("org.tpolecat", doobieV, "doobie-specs2"),
    testDep("org.specs2", "4.9.3", "specs2-core")
  ).flatten

  def testDep(group: String, version: String, pkgs: String*) =
    dep(group, version, pkgs:_*).map(_ % "test").toSeq

  def dep(group: String, version: String, pkgs: String*) =
    pkgs.map(group %% _ % version).toSeq
}

package org.jinilover
package microservice
package web

import java.time.Clock

import cats.data.StateT
import cats.implicits._
import cats.mtl.implicits._
import cats.effect.{IO, Sync}

import fs2.Stream

import io.circe.parser._
import io.circe.Decoder

import org.http4s._
import org.http4s.implicits._

import org.specs2.{ScalaCheck, Specification}

import buildInfo.BuildInfo
import ops.OpsService
import OpsTypes.VersionInfo
import service.LinkService
import LinkTypes.{Link, LinkId, LinkStatus, SearchLinkCriteria, UserId}
import persistence.LinkPersistence

import Mock._
import LinkTypeArbitraries._

/**
  * Tests to ensure `Routes` extract the request and sent to service correctly
  */
class WebApiSpec extends Specification with ScalaCheck {
  lazy val clock = Clock.systemDefaultZone()

  override def is =
    s2"""
      Routes must
        Get   / $welcomeMsgOk
        Get   /version_info $versionInfoOk
        POST  /users/userId/links return bad request when user attempts to add himself $userAddToHimself
        POST  /users/userId/links extracts and passes correct userIds to service $addLink
        POST  /users/userId/links return bad request caused by unique key violation $handleUniqueKeyViolation
        GET   /users/userId/links extract the required query parameter $getLinksWithQueryParams
        GET   /links/linkId for unauthorised req $unauthorisedGetLink
        GET   /links/linkId for existing or non-exist link $getLink
        PUT   /links/linkId for accepting a link $acceptLink
        DELETE /links/linkId return message of deleting existing link $deleteExistingLink
        DELETE /links/linkId return message of deleting non-existing link $deleteNonExistLink
    """

  def welcomeMsgOk = {
    val routes = createRoutes(createService(new DummyPersistence))
    val req = Request[IO](Method.GET, uri"/")

    checkRes(routes.run(req), Status.Ok, "Welcome to REST servce in functional Scala!")
  }

  def versionInfoOk = {
    val routes = createRoutes(createService(new DummyPersistence))
    val req = Request[IO](Method.GET, uri"/version_info")

    checkRes(
      routes.run(req)
    , Status.Ok
    , VersionInfo(
        name = BuildInfo.name
      , version = BuildInfo.version
      , scalaVersion = BuildInfo.scalaVersion
      , sbtVersion = BuildInfo.sbtVersion
      , gitCommitHash = BuildInfo.gitCommitHash
      , gitCommitMessage = BuildInfo.gitCommitMessage
      , gitCommitDate = BuildInfo.gitCommitDate
      , gitCurrentBranch = BuildInfo.gitCurrentBranch)
    )
  }

  def userAddToHimself = prop { (uid1: UserId, uid2: UserId) =>
    val mockDb = new MockDbForAddLink[IO](dummyLinkId)
    val routes = createRoutes(createService(mockDb))
    val req = Request[IO](Method.POST
      , Uri(path = s"/users/${uid1}/links")
      , body = createEntityBody(s""""${uid2.unwrap}""""))
    val resIO = routes.run(req)

    if (uid1 == uid2)
      checkRes(resIO, Status.BadRequest, "Both user ids are the same")
    else
      checkRes(resIO, Status.Ok, s"${dummyLinkId.unwrap}")
  }

  // test to ensure user ids are extracted from request and sent to service correctly
  def addLink = prop { (expectedState: (UserId, UserId)) =>
    val (uid1, uid2) = expectedState
    type MonadStack[A] = StateT[IO, (UserId, UserId), A]

    val mockService = new MockServiceForSuccessAddLink[MonadStack](dummyLinkId)
    val routes = createRoutes(mockService)
    val req = Request[MonadStack](
      Method.POST
    , Uri(path = s"/users/${uid1}/links")
    , body = Stream.emits(s""""${uid2}"""".getBytes).evalMap{ x => StateT(s => IO(s, x)) }
    )

    checkBackendState(
      routes.run(req).run(initial = (armin, annie))
    , expectedState)
  }.setArbitrary(unequalUserIdsPairArbitrary)

  // use a mock service that only throw unique key violation to ensure
  // `WebApi` handles it correctly
  def handleUniqueKeyViolation = {
    val mockService = new MockServiceForUniqueKeyViolation[IO]
    val routes = createRoutes(mockService)
    val req = Request[IO](
      Method.POST
    , uri"/users/eren/links"
    , body = createEntityBody(""""mikasa""""))

    checkRes(routes.run(req), Status.BadRequest, "Link between eren and mikasa already exists")
  }

  // set up all possible query parameters to make sure
  // it extracts/sends them to backend correctly
  // test is similar to `LinkServiceSpec.getLinks`, therefore reuse `MockDbForGetLinks`
  def getLinksWithQueryParams = {
    type MonadStack[A] = StateT[IO, SearchLinkCriteria, A]

    val dummyLog = new MockLogMonadState[MonadStack, SearchLinkCriteria]
    val mockDb = new MockDbForGetLinks[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)
    val routes = createRoutes(service)

    val reqs = List(
      uri"/users/eren/links"
    , uri"/users/eren/links?status=Accepted"
    , uri"/users/eren/links?status=Pending"
    , uri"/users/eren/links?is_initiator=true"
    , uri"/users/eren/links?is_initiator=false"
    , uri"/users/eren/links?is_initiator=true&status=Accepted"
    , uri"/users/eren/links?status=Pending&is_initiator=true" // param order shouldn't matter
    , uri"/users/eren/links?is_initiator=false&status=Accepted"
    , uri"/users/eren/links?status=Pending&is_initiator=false"
    ).map(Request[MonadStack](Method.GET, _))

    val initialState = SearchLinkCriteria(UserId("value_doesnt_matter"))
    val expectedStates = possibleErenSearchCriterias

    reqs.zip(expectedStates).map { case (req, expectedState) =>
      checkBackendState(routes.run(req).run(initialState), expectedState)
    }.reduceLeft(_ and _)
  }

  def unauthorisedGetLink = {
    val routes = createRoutes(createService(new MockDbForGetLink(Map.empty[LinkId, Link])))
    val authReq = Request[IO](Method.GET, uri"/links/any_linkid_doesnt_matter")

    routes.run(authReq).unsafeRunSync().status must be_==(Status.Unauthorized)
  }

  def getLink = {
    val createReq: Uri => Request[IO] = uri =>
      Request[IO](Method.GET, uri,
        headers = Headers.of(Header(name = "Authorization", value = "Bearer eren"))
      )

    val linkId = LinkId("exist_linkid")
    val link = mika_add_eren.copy(id = Some(linkId))
    val cache = Map(linkId -> link)
    val routes = createRoutes(createService(new MockDbForGetLink(cache)))

    checkRes(
      routes.run(createReq(Uri(path = s"/links/${linkId.unwrap}")))
    , Status.Ok
    , List(link)) and
    checkRes(
      routes.run(createReq(uri"/links/non_exist_linkid"))
    , Status.Ok
    , List.empty[Link])
  }

  // it should extract the link id and send to backend correctly
  def acceptLink = prop { (expectedState: LinkId) =>
    type MonadStack[A] = StateT[IO, LinkId, A]

    val mockService = new MockServiceForAcceptLink[MonadStack]
    val routes = createRoutes(mockService)
    val req = Request[MonadStack](Method.PUT, Uri().withPath(s"/links/${expectedState.unwrap}"))

    checkBackendState(
      routes.run(req).run(LinkId("any_id_doesnt_matter"))
    , expectedState)
  }

  // when there is a link
  // delete link in the first should get response that 1 link is deleted
  // rerun the request should get response that the link does not exist
  def deleteExistingLink = {
    val sampleLinkId = "any_id_is_ok"
    val routes = createRoutes(new MockServiceForRemoveOneLink[IO])
    val req = Request[IO](Method.DELETE, Uri(path = s"/links/${sampleLinkId}"))

    checkRes(routes.run(req), Status.Ok, s"Linkid $sampleLinkId removed successfully")
  }

  def deleteNonExistLink = {
    val sampleLinkId = "any_id_is_ok"
    val routes = createRoutes(new MockServiceForRemoveZeroLink[IO])
    val req = Request[IO](Method.DELETE, Uri(path = s"/links/${sampleLinkId}"))

    checkRes(routes.run(req), Status.Ok, s"No need to remove non-exist linkid $sampleLinkId")
  }

  private def createEntityBody(s: String): EntityBody[IO] =
    Stream.emits(s.getBytes).evalMap(x => IO(x))

  private def createService(db: LinkPersistence[IO]): LinkService[IO] =
    LinkService.default(db, clock, Log.default.unsafeRunSync())

  private def createRoutes[F[_]: Sync](service: LinkService[F]): HttpApp[F] =
    WebApi.default[F](OpsService.default, service).routes

  private def checkRes[A: Decoder](
    resIO: IO[Response[IO]]
  , expectedStatus: Status
  , expectedBody: A) = {
    for {
      res <- resIO
      bodyText <- res.bodyAsText.compile.toList.map(_.head)
    } yield {
      val body = parse(bodyText).flatMap(_.as[A])
      (res.status must be_==(expectedStatus)) and (body must be_==(Right(expectedBody)))
    }
  }.unsafeRunSync()

  private def checkBackendState[S](
    resIO: IO[(S, Response[StateT[IO, S, ?]])]
  , expectedNextState: S) =
    resIO.map { case (s, res) =>
      (s must be_==(expectedNextState)) and (res.status must be_==(Status.Ok))
    }.unsafeRunSync()
}

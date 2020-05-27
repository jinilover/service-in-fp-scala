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
import io.circe.Error

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
class RoutesSpec extends Specification with ScalaCheck {
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
        GET   /links/linkId for existing or non-exist link $getLink
        PUT   /links/linkId for accepting a link $acceptLink
        DELETE /links/linkId authorisation check and return message accordingly $deleteLink
    """

  def welcomeMsgOk = {
    val routes = createRoutes(createLinkService(new DummyPersistence))

    val expected = List(""""Welcome to REST servce in functional Scala!"""")
    val req = Request[IO](Method.GET, uri"/")
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    (res.status must be_==(Status.Ok)) and
      (bodyText must be_==(expected))
  }

  def versionInfoOk = {
    val routes = createRoutes(createLinkService(new DummyPersistence))

    val expected = List(Right(VersionInfo(
        name = BuildInfo.name
      , version = BuildInfo.version
      , scalaVersion = BuildInfo.scalaVersion
      , sbtVersion = BuildInfo.sbtVersion
      , gitCommitHash = BuildInfo.gitCommitHash
      , gitCommitMessage = BuildInfo.gitCommitMessage
      , gitCommitDate = BuildInfo.gitCommitDate
      , gitCurrentBranch = BuildInfo.gitCurrentBranch
      )))
    val req = Request[IO](Method.GET, uri"/version_info")
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    (res.status must be_==(Status.Ok)) and
      (bodyText.map(s => parse(s).flatMap(_.as[VersionInfo])) must be_==(expected))
  }

  // make sure Routes return different response status
  // if user tries to add himself
  def userAddToHimself = prop { (uid1: UserId, uid2: UserId) =>
    val mockDb = new MockDbForAddLink[IO](dummyLinkId)
    val routes = createRoutes(createLinkService(mockDb))

    val req = Request[IO](
      Method.POST
    , Uri().withPath(s"/users/${uid1}/links")
    , body = createEntityBody(s""""${uid2}"""")
    )
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)

    if (uid1 == uid2)
      (res.status must be_==(Status.BadRequest)) and
        (bodyText must be_==(List(""""Both user ids are the same"""")))
    else
      (res.status must be_==(Status.Ok)) and
        (bodyText must be_==(List(s""""${dummyLinkId.unwrap}"""")))
  }

  // test to ensure user ids are extracted from request and sent to service correctly
  def addLink = prop { (uidPair: (UserId, UserId)) =>
    val (uid1, uid2) = uidPair

    type MonadStack[A] = StateT[IO, (UserId, UserId), A]

    val mockService = new MockServiceForSuccessAddLink[MonadStack](dummyLinkId)
    val routes = createRoutes(mockService)
    val req = Request[MonadStack](
      Method.POST
    , Uri().withPath(s"/users/${uid1}/links")
    , body = Stream.emits(s""""${uid2}"""".getBytes).evalMap{ x => StateT(s => IO(s, x)) }
    )

    val initialState = (armin, annie) // any userid is ok as the state should be overwritten by execution
    val (userIds, res) = routes.routes.run(req).run(initialState).unsafeRunSync

    (res.status must be_==(Status.Ok)) and (userIds must be_==(uidPair))
  }.setArbitrary(unequalUserIdsPairArbitrary)

  // use a mock service that only throw unique key violation to ensure
  // `Routes` handles it correctly
  def handleUniqueKeyViolation = {
    val mockService = new MockServiceForUniqueKeyViolation[IO]
    val routes = createRoutes(mockService)
    val req = Request[IO](
      Method.POST
    ,uri"/users/eren/links"
    , body = createEntityBody(""""mikasa"""")
    )
    val res = routes.routes.run(req).unsafeRunSync()
    val bodyText = getBodyText(res)
    val expected = List(""""Link between eren and mikasa already exists"""")

    (res.status must be_==(Status.BadRequest)) and
      (bodyText must be_==(expected))
  }

  // Test is similar to `LinkServiceSpec.getLinks`, reuse MockDbForGetLinks
  def getLinksWithQueryParams = {
    type MonadStack[A] = StateT[IO, SearchLinkCriteria, A]

    val createReq: Uri => Request[MonadStack] = Request[MonadStack](Method.GET, _)

    val dummyLog = new MockLogMonadState[MonadStack, SearchLinkCriteria]
    val mockDb = new MockDbForGetLinks[MonadStack](Nil)
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)
    val routes = createRoutes(service)

    val reqs = List(
      createReq(uri"/users/eren/links")
    , createReq(uri"/users/eren/links?status=Pending")
    , createReq(uri"/users/eren/links?status=Accepted")
    , createReq(uri"/users/eren/links?is_initiator=true")
    , createReq(uri"/users/eren/links?is_initiator=false")
    , createReq(uri"/users/eren/links?is_initiator=false&status=Accepted")
    , createReq(uri"/users/eren/links?status=Pending&is_initiator=true")
    )

    val criteriasSentToDb =
      reqs.traverse { req => routes.routes.run(req).run(erenSearchCriteria)}
        .map { list => list.map(_._1) }
        .unsafeRunSync()
    val expectedResult = List[SearchLinkCriteria => SearchLinkCriteria](
      identity
    , _.copy(linkStatus = Some(LinkStatus.Pending))
    , _.copy(linkStatus = Some(LinkStatus.Accepted))
    , _.copy(isInitiator = Some(true))
    , _.copy(isInitiator = Some(false))
    , _.copy(isInitiator = Some(false), linkStatus = Some(LinkStatus.Accepted))
    , _.copy(isInitiator = Some(true), linkStatus = Some(LinkStatus.Pending))
    ).map(f => f(erenSearchCriteria))

    criteriasSentToDb must be_==(expectedResult)
  }

  def getLink = {
    val createReq: Uri => Request[IO] = Request[IO](Method.GET, _)

    val existLinkId = LinkId("exist_linkid")
    val existLink = mika_add_eren.copy(id = Some(existLinkId))
    val dbCache: Map[LinkId, Link] = Map(existLinkId -> existLink)
    val routes = createRoutes(createLinkService(new MockDbForGetLink(dbCache)))

    type DecodeResult[A] = Either[Error, A]

    val decodeResults =
      List(
        uri"/links/exist_linkid"     // should return mika_add_eren
      , uri"/links/non_exist_linkid" // should not return any link
      ).map(createReq)
      .traverse { req =>
        for {
          res <- routes.routes.run(req)
          multiStrs <- res.bodyAsText.compile.toList
        } yield multiStrs
                .traverse{ parse(_).flatMap(_.as[List[Link]]) }
                .map(_.flatten)
      }.map(_.sequence)
      .unsafeRunSync()
    val expectedResult = Right(List(List(existLink), Nil))

    decodeResults must be_==(expectedResult)
  }

  def acceptLink = {
    type MonadStack[A] = StateT[IO, LinkId, A]

    val mockService = new MockServiceForAcceptLink[MonadStack]
    val routes = createRoutes(mockService)

    val req = Request[MonadStack](Method.PUT, uri"/links/linkid_be_accepted")
    val linkIdSentToService = routes.routes.run(req).run(LinkId("")).map(_._1).unsafeRunSync()

    linkIdSentToService.unwrap must be_==("linkid_be_accepted")
  }

  // Test is similar to `LinkServiceSpec.removeLink`, reuse MockDbForRemoveLink
  def deleteLink = {
    type MonadStack[A] = StateT[IO, Int, A]

    val dummyLog = new MockLogMonadState[MonadStack, Int]
    val mockDb = new MockDbForRemoveLink[MonadStack]
    val service = LinkService.default[MonadStack](mockDb, clock, dummyLog)
    val routes = createRoutes(service)

    // test unauthorised req
    val unauthReq = Request[MonadStack](Method.DELETE,uri"/links/any_id_is_ok")
    val (_, unauthRes) = routes.routes.run(unauthReq).run(1).unsafeRunSync()

    // test authorised req
    val authReq = Request[MonadStack](
      Method.DELETE,
      uri"/links/any_id_is_ok",
      headers = Headers.of(Header(name = "Authorization", value = "Bearer eren"))
    )
    // similar to `LinkServiceSpec.remove`, run twice to make sure different messages are returned
    val authResult: List[String] =
      List.fill(2)(authReq).traverse { req =>
        routes.routes.run(req) >>= (res => res.bodyAsText.compile.toList)
      }.map(_.flatten)
      .run(1) // mimic there is 1 link to be deleted in the beginning
      .map(_._2)
      .unsafeRunSync()
    val expectedAuthResult = List(
      """"Linkid any_id_is_ok removed successfully""""
    , """"No need to remove non-exist linkid any_id_is_ok""""
    )

    (unauthRes.status must be_==(Status.Unauthorized)) and
      (authResult must be_==(expectedAuthResult))
  }

  private def createEntityBody(s: String): EntityBody[IO] =
    Stream.emits(s.getBytes).evalMap(x => IO(x))

  private def getBodyText(res: Response[IO]): List[String] =
    res.bodyAsText.compile.toList.unsafeRunSync()

  private val createLinkService: LinkPersistence[IO] => LinkService[IO] = {
    val log = Log.default.unsafeRunSync()
    LinkService.default(_, clock, log)
  }

  private def createRoutes[F[_]: Sync](service: LinkService[F]): Routes[F] =
    Routes.default[F](OpsService.default, service)

}

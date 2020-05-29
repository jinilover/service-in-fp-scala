# Knowledge sharing
There is plenty of information about these functional libraries from the web.  I would like to share some interesting thing I came across from this project.

## Http4s

### Testing w/o service startup
One thing I like http4s is I don't need to startup the service to test the REST api routes.  I can do the unit-tests with the corresponding mocks behind the routes.  Please refer to https://http4s.org/v0.21/testing/ and `WebApiSpec.scala` for details.

### Middleware is easy to use
The http4s middleware is just a wrapper around a route that provides a means of manipulating the request, and/or the response returned by the route.  

I use the built in authentication middleware to wrapper a route for `GET /links/:linkId`  The request is checked for an `Authorization` header of a `Bearer` token before calling the service.  Absence of the authorization information will get an `401` response.  Please refer to https://http4s.org/v0.21/auth/ and `WebApi` for details.

### References
* https://github.com/http4s/http4s
* https://http4s.org/v0.21/

## Mtl
https://github.com/jinilover/mtl-classy-prism explains what is mtl and the problem it solves.  It is a Haskell project but the concept is more easily understood in Haskell :slightly_smiling_face:

### How mtl solves my problem
In `LinkService` unit test, it only tests its logic w/o a real database.  Using a mock db that entertains the `LinkService` logic will do the job.  Depending on the requirement, sometimes the mock db should be able to *change* the state.  I once used `var` in the mock db and accidentally discovered the unit test fails to be RT.  Although it is unit test code, I don't want to remember which part is not RT and pay special care for it.  To solve the problem gracefully, I decided to use state monad.  `MonadState` is preferred because it is type class so that any `MonadState` instance is a state monad.

Example
```scala
  class MockDbForRemoveLink[F[_]: Monad]
    (implicit MS: MonadState[F, Int]) // require a MonadState
    extends DummyPersistence[F] {

    override def remove(id: LinkId): F[Int] =
      MS.get <* MS.modify(_ - 1) // state changed after call
  }
```

To instantiate `MockDbForRemoveLink`, I should choose an `F` that fulfills both `Monad` and `MonadState` requirement.

Example
```scala
new MockDbForRemoveLink[StateT[IO, Int, ?]]
```

### If there are multiple type classes involved
Sometimes the mock db does not only update the state but also raise error in some conditions.  It requires both `MonadState` and `MonadError`.

Example
```scala
  class MockDbViolateUniqueKey[F[_]]
      (sampleLinkId: LinkId)
      (implicit MS: MonadState[F, Set[String]], ME: MonadError[F, Throwable])
    extends DummyPersistence[F] {

    override def add(link: LinkTypes.Link): F[LinkId] = {
      val uniqueKey = linkKey(link.initiatorId, link.targetId)
      MS.get.flatMap { set =>
        if (set contains uniqueKey)
          ME.raiseError(new RuntimeException("""violates unique constraint "unique_unique_key""""))
        else
          MS.set(set + uniqueKey) *> ME.pure(sampleLinkId)
      }
    }
  }
```

To instantiate `MockDbViolateUniqueKey`, I should choose an `F` that fulfills both `MonadState` and `MonadError` requirement.

Both of the following examples are valid.
```scala
new MockDbViolateUniqueKey[EitherT[StateT[IO, Set[String], ?], Throwable, ?]]
```
```scala
new MockDbViolateUniqueKey[StateT[EitherT[IO, Throwable, ?], Set[String], ?]]
```

* First example is valid because 
    * There is `MonadError[EitherT[F, E, ?], E]` instance and
    * There is `MonadState[EitherT[F, E, ?], S]` instance whose implementation requires a `MonadState[F, S]` instance which is `MonadState[StateT[M, S, ?], S]` here.  In this example, `M` is `IO`, `S` is `Set[String]`, `E` is `Throwable`.  Therefore the concrete answer is `EitherT[StateT[IO, Set[String], ?], Throwable, ?]`
* Second example is also valid because
    * There is `MonadState[StateT[F, S, ?], S]` instance and
    * There is `MonadError[StateT[F, S, ?], E]` instance whose implementation requires a `MonadError[F, E]` instance which is `MonadError[EitherT[M, E, ?], E]`.  Therefore the concrete answer is `StateT[EitherT[IO, Throwable, ?], Set[String], ?]`

I don't know exactly where are these instances located :sweat_smile:  To make it compile, import the following packages
```scala
import cats.data.{EitherT, StateT}
import cats.implicits._
import cats.mtl.implicits._
```

### Reference
* https://typelevel.org/cats-mtl/getting-started.html

## Doobie
???

## Lesson
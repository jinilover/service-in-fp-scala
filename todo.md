# Things to do

## Find mutual links
For a given user id, find all the user ids who have common *friends* with the user but not linked with the user yet.

## Use Hikari db pooling 
Currently it creates a transactor by `Transactor.fromDriverManager`  
### Reference
https://tpolecat.github.io/doobie/docs/14-Managing-Connections.html

## Bracket
Use bracket to clean up resource when the application is finished or when it fails.  https://typelevel.org/cats-effect/

## Retry policy
Cats `IO` doesn't provide retry policy.  `cats-retry` https://github.com/cb372/cats-retry provides a solution for it.

## Zio
Zio's `IO` supports more features
* `IO` have have better error handling
* As mentioned before, Zio provides retry policy
Things to be considered when switching to Zio
* Starting up the http4s service requires implicit instances of `ContextShift[F[_]]` and `Timer[F[_]]`.  The instances are created by `IOApp`.  These type classes belong to cats effect.  There should be instances of Zio `IO`.
* `MonadError` is used in `LinkService` implementation.  There is `MonadError[IO]` from cats effect.  It should consider how to provide a similar solution in using Zio `IO`. 

## Swagger api docs
Http4s support swagger api docs by `rho-swagger`.  https://github.com/http4s/rho

## Integration test
Test on the dockerized application 
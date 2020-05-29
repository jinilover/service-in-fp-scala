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
???

## Doobie
???
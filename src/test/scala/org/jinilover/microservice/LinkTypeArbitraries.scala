package org.jinilover
package microservice

import org.scalacheck.{Arbitrary, Gen}

import org.jinilover.microservice.LinkTypes.UserId
import Mock._

object LinkTypeArbitraries {
  implicit val userIdPairArbitrary: Arbitrary[(UserId, UserId)] =
    Arbitrary {
      for {
        u1 <- Gen.oneOf(sampleUserIds)
        u2 <- Gen.oneOf(sampleUserIds.filterNot(_ == u1))
      } yield (u1, u2)
    }
}

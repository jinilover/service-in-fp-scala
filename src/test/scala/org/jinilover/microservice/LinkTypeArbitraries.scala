package org.jinilover
package microservice

import org.scalacheck.{Arbitrary, Gen}

import org.jinilover.microservice.LinkTypes.UserId
import Mock._

object LinkTypeArbitraries {
  implicit val unequalUserIdsPairArbitrary: Arbitrary[(UserId, UserId)] =
    Arbitrary {
      for {
        u1 <- userIdArbitrary.arbitrary
        u2 <- Gen.oneOf(sampleUserIds.filterNot(_ == u1))
      } yield (u1, u2)
    }

  implicit val userIdArbitrary: Arbitrary[UserId] =
    Arbitrary {
      Gen.oneOf(sampleUserIds)
    }
}

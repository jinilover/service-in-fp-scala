package org.jinilover
package microservice

import org.scalacheck.{Arbitrary, Gen}
import org.jinilover.microservice.LinkTypes.{LinkId, LinkStatus, UserId}
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

  implicit val linkIdArbitrary: Arbitrary[LinkId] =
    Arbitrary {
      Gen.uuid.map(uuid => LinkId(uuid.toString))
    }

  implicit val linkStatusArbitaray: Arbitrary[LinkStatus] =
    Arbitrary {
      Gen.oneOf(LinkStatus.Pending, LinkStatus.Accepted)
    }
}

package org.jinilover.microservice

import java.time.Clock

import org.jinilover.microservice.LinkTypes.{Link, SearchLinkCriteria, UserId}

object MockData {
  val clock = Clock.systemDefaultZone()

  // sample user id
  val List(mikasa, eren, armin, annie, reiner, bert, levi, erwin) =
    List("mikasa", "eren", "armin", "annie", "reiner", "bert", "levi", "erwin").map(UserId.apply)

  // sample links
  val List(
      mika_add_eren
    , reiner_add_eren
    , bert_add_eren
    , eren_add_armin
    , eren_add_annie
    , eren_add_levi
    , eren_add_erwin) =
    List( (mikasa, eren)
      , (reiner, eren)
      , (bert, eren)
      , (eren, armin)
      , (eren, annie)
      , (eren, levi)
      , (eren, erwin) )
      .map { case (initiator, target) =>
        Link(initiatorId = initiator
          , targetId = target
          , status = LinkStatus.Pending
          , creationDate = clock.instant)
      }

  val simpleSearch = SearchLinkCriteria(userId = eren)
}

package org.jinilover

package object microservice {
  def toLinkStatus(s: String): LinkStatus = {
    if (s.toUpperCase == LinkStatus.Pending.toString.toUpperCase)
      LinkStatus.Pending
    else
      LinkStatus.Accepted
  }
}

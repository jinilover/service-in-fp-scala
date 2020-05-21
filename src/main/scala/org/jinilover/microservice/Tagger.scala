package org.jinilover.microservice

import scalaz.{@@, Tag}

trait Tagger[A] {
  sealed trait Marker

  def apply(a: A): A @@ Marker = Tag(a)
}
package org

import scalaz.{@@, Tag}

package object jinilover {
  trait Tagger[A] {
    sealed trait Marker

    def apply(a: A): A @@ Marker = Tag(a)
  }

  implicit class TaggedOps[A, T](val a: A @@ T) extends AnyVal {
    def unwrap: A = Tag.unwrap(a)
  }

}

package wust.util

trait Empty[+T] {
  @inline def empty: T
}

object Empty {
  @inline def apply[T](implicit e: Empty[T]): T = e.empty

  implicit object StringEmpty extends Empty[String] {
    @inline def empty = ""
  }
  implicit object OptionEmpty extends Empty[Option[Nothing]] {
    @inline def empty = Option.empty
  }
  implicit object SeqEmpty extends Empty[Seq[Nothing]] {
    @inline def empty = Seq.empty
  }
  implicit def SetEmpty[T] = new Empty[Set[T]] {
    @inline def empty = Set.empty
  }
}

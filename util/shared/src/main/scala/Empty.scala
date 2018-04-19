package wust.util

trait Empty[+T] {
  def empty: T
}

object Empty {
  implicit object OptionEmpty extends Empty[Option[Nothing]] {
    def empty = Option.empty
  }
  implicit object SeqEmpty extends Empty[Seq[Nothing]] {
    def empty = Seq.empty
  }
  implicit def SetEmpty[T] = new Empty[Set[T]] {
    def empty = Set.empty
  }
}

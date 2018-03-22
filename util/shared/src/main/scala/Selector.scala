package wust.util

trait Selector[T] extends (T => Boolean) {
  import Selector._
  def intersect(that: Selector[T]): Selector[T] = Intersect(this, that)
  def union(that: Selector[T]): Selector[T] = Union(this, that)
  def apply(elem: T): Boolean
}

//TODO: use dogs for ISet/Set
object Selector {
  def All[T] = new Selector[T] { override def apply(elem:T) = true }
  def None[T] = new Selector[T] { override def apply(elem:T) = false }
  case class Predicate[T](set: T => Boolean) extends Selector[T] {
    override def apply(elem: T) = set(elem)
  }
  case class Union[T](a: Selector[T], b: Selector[T]) extends Selector[T] {
    def apply(elem: T) = a(elem) || b(elem)
  }
  case class Intersect[T](a: Selector[T], b: Selector[T]) extends Selector[T] {
    def apply(elem: T) = a(elem) && b(elem)
  }
}

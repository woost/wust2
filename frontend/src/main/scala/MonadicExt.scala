package mhtml

import util.Pipe

trait WriteVar[A] {
  def :=(newValue: A): Unit
  def update(f: A => A): Unit
  def writeProjection[B](to: B => A, from: PartialFunction[A, B] = PartialFunction.empty): WriteVar[B] = WriteProjection(this, to, from)
}
object WriteVar {
  implicit def VarIsWriteVar[A](v: Var[A]) = new WriteVar[A] {
    def :=(newValue: A) = v := newValue
    def update(f: A => A) = v.update(f)
  }
}
object WriteProjection {
  def apply[S, A](v: WriteVar[S], to: A => S, from: PartialFunction[S, A]): WriteVar[A] = new WriteVar[A] {
    def :=(newValue: A) = v := to(newValue)
    def update(f: A => A) = v.update((from andThen f andThen to) orElse { case i => i })
  }
}

class RxVar[S, A](write: WriteVar[S], rx: Rx[A]) extends WriteVar[S] with Rx[A] {
  import RxVar.RichRx

  override def :=(newValue: S) = write := newValue
  override def update(f: S => S) = write.update(f)
  override def writeProjection[T](to: T => S, from: PartialFunction[S, T]): RxVar[T, A] = RxVar(write.writeProjection(to, from), rx)

  override def value = rx.value
  override def foreachNext(f: A => Unit) = rx.foreachNext(f)
  override def foreach(f: A => Unit) = rx.foreach(f)
  override def map[B](f: A => B): RxVar[S, B] = RxVar(write, rx.map(f))
  override def flatMap[B](f: A => Rx[B]): RxVar[S, B] = RxVar(write, rx.flatMap(f))
  def mapWithPrevious[B](initial: B)(f: (B, A) => B): RxVar[S, B] = RxVar(write, rx.mapWithPrevious(initial)(f))
}
object RxVar {
  def apply[S, A](write: WriteVar[S], rx: Rx[A]): RxVar[S, A] = new RxVar(write, rx)
  def apply[S](value: S): RxVar[S, S] = VarIsRxVar(Var(value))

  implicit def VarIsRxVar[A](v: Var[A]) = new RxVar(v, v)

  implicit class SymmetricRxVar[A](rxVar: RxVar[A, A]) {
    def projection[B](to: B => A, from: A => B) = rxVar.map(from).writeProjection(to, { case v => from(v) })
  }

  implicit class RichRx[A](rx: Rx[A]) {
    def mapWithPrevious[B](initial: B)(f: (B, A) => B): Rx[B] = {
      var prev = f(initial, rx.value)
      rx.map { curr =>
        val next = f(prev, curr)
        prev = next
        next
      }
    }

    def debug: Rx[A] = { debug() }
    def debug(name: String = ""): Rx[A] = {
      rx.foreach(x => println(s"$name: $x"))
      rx
    }
  }

  // instead of the defined implicits, which require exactly one type parameter for a subclass of rx:
  // https://github.com/OlivierBlanvillain/monadic-html/blob/40a7e2963238cb286651cf539e6f680b579f00d3/monadic-html/src/main/scala/scala/xml/xml.scala#L195
  import scala.xml.{XmlElementEmbeddable, XmlAttributeEmbeddable}
  implicit def sourceVarElementEmbeddable[S, A] = XmlElementEmbeddable.atom[RxVar[S, A]]
  implicit def sourceVarAttributeEmbeddable[S, A] = XmlAttributeEmbeddable.atom[RxVar[S, A]]
}

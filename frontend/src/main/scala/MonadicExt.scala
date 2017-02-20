package mhtml

trait WriteVar[A] {
  def :=(newValue: A): Unit
  def update(f: A => A): Unit
  def writeProjection[B](from: PartialFunction[A,B], to: B => A): WriteVar[B] = WriteProjection(this, from, to)
}
object WriteVar {
  implicit def VarIsWriteVar[A](v: Var[A]) = new WriteVar[A] {
    def :=(newValue: A) = v := newValue
    def update(f: A => A) = v.update(f)
  }
}
object WriteProjection {
  def apply[S, A](v: WriteVar[S], from: PartialFunction[S,A], to: A => S): WriteVar[A] = new WriteVar[A] {
    def :=(newValue: A) = v := to(newValue)
    def update(f: A => A) = v.update(from andThen f andThen to)
  }
}

class VarRx[S, A](write: WriteVar[S], rx: Rx[A]) extends WriteVar[S] with Rx[A] {
  override def :=(newValue: S) = write := newValue
  override def update(f: S => S) = write.update(f)
  override def writeProjection[T](from: PartialFunction[S,T], to: T => S): VarRx[T, A] = VarRx(WriteProjection(write, from, to), rx)

  override def value = rx.value
  override def foreachNext(f: A => Unit) = rx.foreachNext(f)
  override def foreach(f: A => Unit) = rx.foreach(f)
  override def map[B](f: A => B): VarRx[S, B] = VarRx(write, rx.map(f))
  override def flatMap[B](f: A => Rx[B]): VarRx[S, B] = VarRx(write, rx.flatMap(f))
}
object VarRx {
  def apply[S, A](write: WriteVar[S], rx: Rx[A]) = new VarRx(write, rx)
  def apply[S](value: S) = VarIsVarRx(Var(value))

  implicit def VarIsVarRx[A](v: Var[A]) = new VarRx(v, v)

  implicit class SymmetricVarRx[A](varRx: VarRx[A,A]) {
    def projection[B](from: PartialFunction[A,B], to: B => A) = varRx
      .writeProjection(from, to)
      .map(from)
  }

  // instead of the defined implicits, which require exactly one type parameter for a subclass of rx:
  // https://github.com/OlivierBlanvillain/monadic-html/blob/40a7e2963238cb286651cf539e6f680b579f00d3/monadic-html/src/main/scala/scala/xml/xml.scala#L195
  import scala.xml.{XmlElementEmbeddable, XmlAttributeEmbeddable}
  implicit def sourceVarElementEmbeddable[S,A] = XmlElementEmbeddable.atom[VarRx[S,A]]
  implicit def sourceVarAttributeEmbeddable[S,A] = XmlAttributeEmbeddable.atom[VarRx[S,A]]
}

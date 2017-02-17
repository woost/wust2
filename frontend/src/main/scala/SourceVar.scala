package mhtml

class SourceVar[S, A](source: Var[S], mapping: Rx[S] => Rx[A]) extends Rx[A] {
  private val target = mapping(source)

  def :=(newValue: S) = source := newValue
  def update(f: S => S) = source.update(f)

  override def value = target.value
  override def foreachNext(s: A => Unit) = target.foreach(s)
  override def foreach(s: A => Unit) = target.foreach(s)
  override def map[B](f: A => B): SourceVar[S, B] = new SourceVar(source, (_: Rx[S]) => target.map(f))
  override def flatMap[B](s: A => Rx[B]): SourceVar[S, B] = new SourceVar(source, (_: Rx[S]) => target.flatMap(s))
}

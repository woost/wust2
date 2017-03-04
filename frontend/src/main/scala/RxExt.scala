package frontend
import rx._

import util.Pipe

trait WriteVar[A] {
  def :=(newValue: A): Unit
  def update(f: A => A): Unit
  def writeProjection[B](to: B => A, from: PartialFunction[A, B] = PartialFunction.empty): WriteVar[B] = WriteProjection(this, to, from)
}
object WriteVar {
  implicit def VarIsWriteVar[A](v: Var[A]) = new WriteVar[A] {
    def :=(newValue: A) = v() = newValue
    def update(f: A => A) = v() = f(v.now) //TODO: v.update(f)
  }
}
object WriteProjection {
  def apply[S, A](v: WriteVar[S], to: A => S, from: PartialFunction[S, A]): WriteVar[A] = new WriteVar[A] {
    def :=(newValue: A) = v := to(newValue)
    def update(f: A => A) = v.update((from andThen f andThen to) orElse { case i => i })
  }
}

class RxVar[S, A](val write: WriteVar[S], val rx: Rx[A]) extends WriteVar[S] {
  import RxVar.RichRx

  override def :=(newValue: S) = write := newValue
  override def update(f: S => S) = write.update(f)
  override def writeProjection[T](to: T => S, from: PartialFunction[S, T]): RxVar[T, A] = RxVar(write.writeProjection(to, from), rx)

  def now = rx.now
  def foreach(f: A => Unit)(implicit ctx: Ctx.Owner) = rx.foreach(f)
  def map[B](f: A => B)(implicit ctx: Ctx.Owner): RxVar[S, B] = RxVar(write, rx.map(f))
  def flatMap[B](f: A => Rx[B])(implicit ctx: Ctx.Owner): RxVar[S, B] = RxVar(write, rx.flatMap(f))
  // def combine[B](f: A => Ctx.Owner => B)(implicit ctx: Ctx.Owner): RxVar[S, B] = RxVar(write, Rx { f(rx())(ctx) }) //TODO
}

object RxVar {
  def apply[S, A](write: WriteVar[S], rx: Rx[A]): RxVar[S, A] = new RxVar(write, rx)
  def apply[S](value: S): RxVar[S, S] = VarIsRxVar(Var(value))

  implicit def VarIsRxVar[A](v: Var[A]) = new RxVar(v, v)

  implicit def RxVarToRx[S, A](rxVar: RxVar[S, A]): Rx[A] = rxVar.rx

  implicit class SymmetricRxVar[A](val rxVar: RxVar[A, A]) extends AnyVal {
    def projection[B](to: B => A, from: A => B)(implicit ctx: Ctx.Owner) = rxVar.map(from).writeProjection(to, { case v => from(v) })
  }

  implicit class RichRx[A](val rx: Rx[A]) extends AnyVal {
    // def combine[B](f: A => Rx[B])(implicit ctx: Ctx.Owner): Rx[B] = Rx{ rx.map(f)) }
    def debug(implicit ctx: Ctx.Owner): Rx[A] = { debug() }
    def debug(name: String = "")(implicit ctx: Ctx.Owner): Rx[A] = {
      rx ||> (_.foreach(x => println(s"$name: $x")))
    }
    def debug(print: A => String)(implicit ctx: Ctx.Owner): Rx[A] = {
      rx ||> (_.foreach(x => println(print(x))))
    }
  }
}

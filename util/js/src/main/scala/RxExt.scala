package object rxext {

  import rx._
  import wust.util.Pipe

  implicit class RichVar[A](val rxVar:Var[A]) extends AnyVal {
    def updatef(f: A => A) = rxVar() = f(rxVar.rx.now)
  }

  implicit class RichRxVar[S,A](val rxVar:RxVar[S,A]) extends AnyVal {
    def map[T](to: A => T)(implicit ctx: Ctx.Owner):RxVar[S,T] = RxVar(rxVar, rxVar.rx.map(to))
    def updatef(f: A => S) = rxVar() = f(rxVar.rx.now)
  }

  implicit class SymmetricRxVar[A](val rxVar: RxVar[A, A]) extends AnyVal {
    def projection[B](to: B => A, from: A => B)(implicit ctx: Ctx.Owner) = rxVar.map(from).writeProjection(to)
  }

  implicit class RichRx[A](val rx: Rx[A]) extends AnyVal {
    // def combine[B](f: A => Rx[B])(implicit ctx: Ctx.Owner): Rx[B] = Rx{ rx.map(f)) }
    def debug(implicit ctx: Ctx.Owner): Rx[A] = { debug() }
    def debug(name: String = "")(implicit ctx: Ctx.Owner): Rx[A] = {
      rx sideEffect (_.foreach(x => println(s"$name: $x")))
    }
    def debug(print: A => String)(implicit ctx: Ctx.Owner): Rx[A] = {
      rx sideEffect (_.foreach(x => println(print(x))))
    }
  }
}

package wust.sdk

import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import rx._

import scala.concurrent.Future

trait IncStore[State, InAction, OutAction] { self =>
  def initialState: State
  def inAction: Observer[InAction]
  def output: Observable[(State, OutAction)]
  val state: Observable[State] = output.map(_._1)
  val outAction: Observable[OutAction] = output.map(_._2)

  def mapIncremental[NextState, NextOutAction](mapInputState:State => NextState, onAction:(NextState,OutAction) => (NextState, NextOutAction)): IncStore[NextState, InAction, NextOutAction] = new IncStore[NextState, InAction, NextOutAction] {
    override def initialState: NextState = mapInputState(self.initialState)
    override def inAction: Observer[InAction] = self.inAction
    override def output: Observable[(NextState, NextOutAction)] = self.output.map { case (state, action) => onAction(mapInputState(state), action)}
  }

  def map[NextState](mapInputState:State => NextState, onAction:(NextState,OutAction) => NextState): IncStore[NextState, InAction, OutAction] = new IncStore[NextState, InAction, OutAction] {
    override def initialState: NextState = mapInputState(self.initialState)
    override def inAction: Observer[InAction] = self.inAction
    override def output: Observable[(NextState, OutAction)] = self.output.map { case (state, action) => (onAction(mapInputState(state), action), action) }
  }

  def mapState(mapInputState: State => State): IncStore[State, InAction, OutAction] = new IncStore[State, InAction, OutAction] {
    override def initialState: State = mapInputState(self.initialState)
    override def inAction: Observer[InAction] = self.inAction
    override def output: Observable[(State, OutAction)] = self.output.map { case (state, action) => (mapInputState(state), action) }
  }
}

case class ConcreteIncStore[State, InAction, OutAction](initialState: State, inAction: Observer[InAction], output: Observable[(State, OutAction)]) extends IncStore[State, InAction, OutAction]

object IncStore {
  type Store[State, Action] = IncStore[State, Action, Action]

  def apply[State, Action](init:State, f:(State,Action) => State): Store[State, Action] = {
    val subject = PublishSubject[Action]()
    var lastState = init
    val observable = subject.map { inAction =>
      lastState = f(lastState, inAction)
      (lastState, inAction)
    }
    new ConcreteIncStore[State, Action, Action](init, subject, observable)
  }
  def incremental[State, InAction, OutAction](init: State, f:(State,InAction) => (State, OutAction)): IncStore[State, InAction, OutAction] = {
    val subject = PublishSubject[InAction]()
    var lastState = init
    val observable = subject.map { inAction =>
      val result = f(lastState, inAction)
      lastState = result._1
      result
    }
    new ConcreteIncStore[State, InAction, OutAction](init, subject, observable)
  }

//  def fromRx[T](rx: Rx[T])(implicit owner:Ctx.Owner): Store[T, T] = {
//    val subject = rxToMonix(rx)
//    new ConcreteIncStore[T, T, T](subject, subject)
//  }
//
//  private def rxToMonix[T](rx: Rx[T])(implicit ctx: Ctx.Owner): Observable[T] with Observer[T] = new Observable[T] with Observer.Sync[T] {
//    override def unsafeSubscribeFn(subscriber: Subscriber[T]): Cancelable = {
//      val obs = rx.foreach(subscriber.onNext)
//      Cancelable(() => obs.kill())
//    }
//    override def onNext(elem: T): Ack = {
//      rx() = elem
//      Ack.Continue
//    }
//    override def onError(ex: Throwable): Unit = throw ex
//    override def onComplete(): Unit = ()
//  }
}

//class IncStore[State, InAction, OutAction](init: State, onAction:(State,InAction) => (State, OutAction)) {
//  private val _state:Var[State] = Var(init)
//  @inline def state:Rx[State] = _state
//  val outAction: PublishSubject[OutAction] = PublishSubject[OutAction]()
//
//  def update(action:InAction):Unit = {
//    val (newState, nextOutAction) = onAction(_state.now, action)
//    _state() = newState
//    outAction.onNext(nextOutAction)
//  }
//
//  def mapIncremental[NextState, NextOutAction](mapInputState:State => NextState, onAction:(NextState,OutAction) => (NextState, NextOutAction))(implicit scheduler: Scheduler): (IncStore[NextState, OutAction, NextOutAction], Cancelable) = {
//    val nextStore = IncStore.incremental(mapInputState(_state.now), onAction)
//    (nextStore, outAction.foreach{ action =>
//      nextStore.update(action)
//      scribe.info("WARNING: Doing Full comparison in incremental Store")
//      assert(mapInputState(_state.now) == nextStore._state.now) //TODO: only assert in dev mode
//    })
//  }
//
//  def map[NextState](mapInputState:State => NextState, onAction:(NextState,OutAction) => NextState)(implicit scheduler: Scheduler): (IncStore[NextState, OutAction, OutAction], Cancelable) = {
//    val nextStore = IncStore(mapInputState(_state.now), onAction)
//    (nextStore, outAction.foreach{ action =>
//      nextStore.update(action)
//      scribe.info("WARNING: Doing Full comparison in incremental Store")
//      assert(mapInputState(_state.now) == nextStore._state.now) //TODO: only assert in dev mode
//    })
//  }
//
//  def mapState(mapState:State => State)(implicit scheduler: Scheduler): (IncStore[State, OutAction, OutAction], Cancelable) = {
//    map[State](mapInputState = mapState, onAction = (state, _) => mapState(state))
//  }
//}
//
//object IncStore {
//  type Store[State, Action] = IncStore[State, Action, Action]
//
//  def apply[State, Action](init:State, f:(State,Action) => State): Store[State, Action] = new Store(init, (state, inAction) => (f(state, inAction), inAction))
//  def incremental[State, InAction, OutAction](init: State, f:(State,InAction) => (State, OutAction)):IncStore[State, InAction, OutAction] = new IncStore(init, f)
//  def fromRx[T](rx:Rx[T])(implicit owner:Ctx.Owner): Store[T, T] = {
//    val store = IncStore[T,T](rx.now, (_,newState) => newState)
//    rx.foreach(store.update)
//    store
//  }
//}

//object StoreExample {
//  def sorted(implicit ctx: Ctx.Owner): VNode = {
//    sealed trait SetAction
//    object SetAction {
//      case class Add(value: Int) extends SetAction
//      case class Remove(value: Int) extends SetAction
//    }
//    sealed trait SeqAction
//    object SeqAction {
//      case class Add(value: Int, index: Int) extends SeqAction
//      case class Remove(index: Int) extends SeqAction
//    }
//    val intList = Store[List[Int], SetAction](Nil, {
//      case (list, action@SetAction.Add(elem))    => elem :: list
//      case (list, action@SetAction.Remove(elem)) => list diff List(elem)
//    })
//
//    val (sortedIntSeq, _) = intList.mapIncremental[Vector[Int], SeqAction](_.sorted.toVector, {
//      case (currentVector, SetAction.Add(nextElem)) =>
//        val (smaller, bigger) = currentVector.partition(_ < nextElem) // can be made O(log n)
//      val position = smaller.size
//        val newState = (smaller :+ nextElem) ++ bigger
//        (newState, SeqAction.Add(nextElem, position))
//      case (currentVector, SetAction.Remove(elem))  =>
//        val position = currentVector.indexOf(elem) // can be made O(log n)
//      val newState = currentVector.take(position) ++ currentVector.drop(1 + position)
//        (newState, SeqAction.Remove(position))
//    })
//
//    val sortedDivs: Observable[ChildCommand] = sortedIntSeq.outAction.map {
//      case SeqAction.Add(elem, index) =>
//        val node = div(
//          display.flex,
//          elem,
//          div("x", paddingLeft := "10px", cursor.pointer, onClick.mapTo(SetAction.Remove(elem)).handleWith(intList.update _))
//        )
//        ChildCommand.Insert(index, node)
//      case SeqAction.Remove(index)    =>
//        ChildCommand.Remove(index)
//    }
//
//    div(
//      button("Add random number", onClick.mapTo(SetAction.Add(scala.util.Random.nextInt(100))).handleWith(intList.update _)),
//      div("intList: ", intList.state.map(_.toString), ", last action: ", intList.outAction.map(_.toString)),
//      div("sorted: ", sortedIntSeq.state.map(_.toString), ", last action: ", sortedIntSeq.outAction.map(_.toString)),
//      div(sortedDivs.startWith(Seq(ChildCommand.ReplaceAll(js.Array(div("initial element")))))),
//    )
//  }
//}

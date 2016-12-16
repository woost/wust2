package frontend

import boopickle.Default._

import diode.Action, Action._
import framework._
import api._

case class SetCounter(value: Int) extends Action

// s"ws://${window.location.host}"
object Client extends AutowireWebsocketClient[ApiEvent](s"ws://localhost:8080")(implicitly[Pickler[ApiEvent]]) {
  val map: PartialFunction[ApiEvent, Action] = {
    case NewCounterValue(value) => SetCounter(value)
  }

  val dispatch: ApiEvent => Unit = map.andThen(a => AppCircuit.dispatch(a)) orElse { case e => println(s"unknown event: $e") }

  def receive(event: ApiEvent) = dispatch(event)
}

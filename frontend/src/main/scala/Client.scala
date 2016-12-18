package frontend

import boopickle.Default._

import diode.Action, Action._
import framework._
import api._

case class SetCounter(value: Int) extends Action

object Client extends WebsocketClient[Channel, ApiEvent] {
  val channelPickler = implicitly[Pickler[Channel]]
  val eventPickler = implicitly[Pickler[ApiEvent]]
  val wireApi = wire[Api]

  val map: PartialFunction[ApiEvent, Action] = {
    case NewCounterValue(value) => SetCounter(value)
  }

  val dispatch: ApiEvent => Unit = map.andThen(a => AppCircuit.dispatch(a)) orElse { case e => println(s"unknown event: $e") }
  def receive(event: ApiEvent) = dispatch(event)

  subscribe(Channel.Counter)
}

package frontend

import framework._
import diode.ActionType
import api._

object DiodeEvent {
  implicit object EventAction extends ActionType[EventType]
}
import DiodeEvent._

object Client extends WebsocketClient {
  def receive(event: EventType) {
    AppCircuit.dispatch(event)
  }
}

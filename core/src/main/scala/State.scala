package wust.backend

import wust.api._
import wust.graph._

case class State(auth: Option[Authentication]) {
  override def toString = s"State($auth)"
}
object State {
  def initial = State(auth = None)

  def applyEvents(state: State, events: Seq[ApiEvent]): State = {
    events.foldLeft(state)(
      (state, event) =>
        event match {
          case ev: ApiEvent.GraphContent => state
          case ev: ApiEvent.AuthContent =>
            state.copy(auth = Some(EventUpdate.createAuthFromEvent(ev)))
        }
    )
  }
}

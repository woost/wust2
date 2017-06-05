package wust.frontend

import rx._
import rxext._
import wust.api._

class EventCache(state: GlobalState, eventHandler: Seq[ApiEvent] => Any)(implicit ctx: Ctx.Owner) {
  private val cachedEvents = new collection.mutable.ArrayBuffer[ApiEvent]

  //TODO why does this not work?
  // private val _eventStream = Var(Seq.empty[ApiEvent])
  // val eventStream: Rx[Seq[ApiEvent]] = _eventStream

  private val _hasEvents = Var(false)
  val hasEvents: Rx[Boolean] = _hasEvents

  def flush(): Unit = {
    state.syncMode.now match {
      case SyncMode.Live =>
        // _eventStream() = cachedEvents
        eventHandler(cachedEvents.toSeq)
        cachedEvents.clear()
      case _ =>
    }

    _hasEvents() = cachedEvents.nonEmpty
  }

  def onEvent(event: ApiEvent) = {
    cachedEvents += event
    flush()
  }
}

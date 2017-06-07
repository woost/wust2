package wust.frontend

import rx._
import rxext._
import wust.api._

class EventCache(state: GlobalState)(implicit ctx: Ctx.Owner) {
  private val cachedEvents = new collection.mutable.ArrayBuffer[ApiEvent]
  private val _hasEvents = Var(false)
  val hasEvents: Rx[Boolean] = _hasEvents

  def flush(): Unit = {
    state.syncMode.now match {
      case SyncMode.Live =>
        state.applyEvents(cachedEvents)
        cachedEvents.clear()
      case _ =>
    }

    _hasEvents() = cachedEvents.nonEmpty
  }

  def addEvents(events: Seq[ApiEvent]) = {
    cachedEvents ++= events
    flush()
  }
}

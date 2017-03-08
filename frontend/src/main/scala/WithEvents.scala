package wust.frontend

import collection.mutable

trait WithEvents[EV] {
  private val listeners = mutable.ArrayBuffer.empty[EV => Unit]
  protected def sendEvent(event: EV) = listeners.foreach(_(event))
  def listen(handler: EV => Unit) = listeners += handler
}

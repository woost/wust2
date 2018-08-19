package shopify.draggable

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.NodeList

import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation._

// https://github.com/Shopify/draggable/tree/master/src/Draggable#api
@js.native
@JSImport("@shopify/draggable", "Draggable")
class Draggable(
    containers: js.Array[html.Element] | NodeList | html.Element,
    options: js.UndefOr[Options] = js.undefined
) extends js.Object {
  def destroy(): Unit = js.native
  def on[R](eventName: String, listener: js.Function0[_]): Draggable = js.native
  def on[E <: AbstractEvent](eventName: String, listener: js.Function1[E, _]): Draggable = js.native
//  def on(eventName: String, listener: js.Function1[dom.Event, Unit]): Draggable = js.native
  def off(eventName: String, listener: js.Function0[Unit]): Draggable =
    js.native
  def off(eventName: String, listener: js.Function1[dom.Event, Unit]): Draggable = js.native
  def getDraggableElements(): js.Array[html.Element] = js.native
  def getDraggableElementsForContainer(container: html.Element): js.Array[html.Element] = js.native
  def addContainer(containers: html.Element*): Draggable = js.native
  def removeContainer(containers: html.Element*): Draggable = js.native
}





@js.native
@JSImport("@shopify/draggable", "Droppable")
class Droppable(
    containers: js.Array[html.Element] | NodeList | html.Element,
    options: js.UndefOr[Options] = js.undefined
) extends Draggable(containers, options)

@js.native
@JSImport("@shopify/draggable", "Sortable")
class Sortable(
    containers: js.Array[html.Element] | NodeList | html.Element,
    options: js.UndefOr[Options] = js.undefined
) extends Draggable(containers, options)

// https://github.com/Shopify/draggable/tree/master/src/Draggable#options
trait Options extends js.Object {
  var draggable: js.UndefOr[String] = js.undefined
  var dropzone: js.UndefOr[String] = js.undefined
  var handle: js.UndefOr[String] = js.undefined
  var delay: js.UndefOr[Double] = js.undefined
  // var plugins: js.UndefOr[js.Array[Plugin]] = js.undefined
  // var sensors: js.UndefOr[js.Array[Sensor]] = js.undefined
  var appendTo: js.UndefOr[String | html.Element | js.Function0[html.Element]] =
    js.undefined
  // var classes: js.UndefOr[Object] = js.undefined

  var mirror: js.UndefOr[MirrorOptions] = js.undefined
}

// https://github.com/Shopify/draggable/blob/master/src/Draggable/Plugins/Mirror
trait MirrorOptions extends js.Object {
  var constrainDimensions: js.UndefOr[Boolean] = js.undefined
}


// AbstractEvent: https://github.com/Shopify/draggable/blob/master/src/shared/AbstractEvent
@js.native
@JSImport("@shopify/draggable", "AbstractEvent")
class AbstractEvent(data: js.Object) extends js.Object {
  def `type`:String = js.native
  def cancelable:String = js.native
  def cancel():Unit = js.native
  def canceled():Boolean = js.native
}


// DragEvent: https://github.com/Shopify/draggable/tree/master/src/Draggable/DragEvent
@js.native
@JSImport("@shopify/draggable", "DragEvent")
class DragEvent(data: js.Object) extends AbstractEvent(data) {
  def source: html.Element = js.native
  def originalSource: html.Element = js.native
  def mirror: html.Element = js.native
  def sourceContainer: html.Element = js.native
  // def sensorEvent: SensorEvent = js.native
}

@js.native
@JSImport("@shopify/draggable", "DragStartEvent")
class DragStartEvent(data: js.Object) extends DragEvent(data) {
}

@js.native
@JSImport("@shopify/draggable", "DragOverEvent")
class DragOverEvent(data: js.Object) extends DragEvent(data) {
  def over: html.Element = js.native
}

@js.native
@JSImport("@shopify/draggable", "DragOutEvent")
class DragOutEvent(data: js.Object) extends DragEvent(data) {
  def over: html.Element = js.native
}


// DroppableEvent: https://github.com/Shopify/draggable/tree/master/src/Droppable/DroppableEvent
@js.native
@JSImport("@shopify/draggable", "DroppableEvent")
class DroppableEvent(data: js.Object) extends AbstractEvent(data)

@js.native
@JSImport("@shopify/draggable", "DroppableDroppedEvent")
class DroppableDroppedEvent(data: js.Object) extends DroppableEvent(data) {
  def dropzone: html.Element = js.native
}

@js.native
@JSImport("@shopify/draggable", "DroppableReturnedEvent")
class DroppableReturnedEvent(data: js.Object) extends DroppableEvent(data) {
  def dropzone: html.Element = js.native
}


// SortableEvent: https://github.com/Shopify/draggable/tree/master/src/Sortable/SortableEvent
@js.native
@JSImport("@shopify/draggable", "SortableEvent")
class SortableEvent(data: js.Object) extends AbstractEvent(data) {
  def dragEvent:DragEvent = js.native
}

@js.native
@JSImport("@shopify/draggable", "SortableStopEvent")
class SortableStopEvent(data: js.Object) extends SortableEvent(data) {
  def oldIndex:Int = js.native
  def newIndex:Int = js.native
  def oldContainer:html.Element = js.native
  def newContainer:html.Element = js.native
}

@js.native
@JSImport("@shopify/draggable", "SortableSortEvent")
class SortableSortEvent(data: js.Object) extends SortableEvent(data) {
  def currentIndex:Int = js.native
  def over:Int = js.native
  def overContainer:html.Element = js.native
}

@js.native
@JSImport("@shopify/draggable", "SortableStartEvent")
class SortableStartEvent(data: js.Object) extends SortableEvent(data) {
  def startIndex:Int = js.native
  def startContainer:html.Element = js.native
}

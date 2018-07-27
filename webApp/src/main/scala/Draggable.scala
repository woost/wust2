package shopify.draggable

import org.scalajs.dom.NodeList
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation._

import org.scalajs.dom
import org.scalajs.dom.raw.{NodeList}
import org.scalajs.dom.html

// https://github.com/Shopify/draggable/tree/master/src/Draggable#api
@js.native
@JSImport("@shopify/draggable", "Draggable")
class Draggable(
    containers: js.Array[html.Element] | NodeList | html.Element,
    options: js.UndefOr[Options] = js.undefined
) extends js.Object {
  def destroy(): Unit = js.native
  def on(eventName: String, listener: js.Function0[Unit]): Draggable = js.native
  def on[E <: AbstractEvent](eventName: String, listener: js.Function1[E, Unit]): Draggable = js.native
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

// https://github.com/Shopify/draggable/blob/master/src/Draggable/Plugins/Mirror/README.md
trait MirrorOptions extends js.Object {
  var constrainDimensions: js.UndefOr[Boolean] = js.undefined
}


@js.native
@JSImport("@shopify/draggable", "AbstractEvent")
class AbstractEvent(data: js.Object) extends js.Object {
  // https://github.com/Shopify/draggable/blob/master/src/shared/AbstractEvent/README.md
  def cancel(data: js.Object):Null = js.native
  def canceled():Boolean = js.native
  val `type`:String = js.native
  val cancelable:String = js.native
}

@js.native
@JSImport("@shopify/draggable", "DragEvent")
class DragEvent(data: js.Object) extends AbstractEvent(data) {
  def source: html.Element = js.native
  def originalSource: html.Element = js.native
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

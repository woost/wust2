package frontend.graphview

import scalajs.js
import org.scalajs.dom
import org.scalajs.d3v4._

abstract class DataSelection[T](
  val container: Selection[dom.EventTarget],
  val tag: String,
  keyFunction: Option[T => Any] = None
) {
  var data: js.Array[T] = js.Array[T]()
  def nodes = container.selectAll(tag)

  def update(newData: js.Array[T]) {
    data = newData

    val element = keyFunction
      .map(container.selectAll(tag).data(newData, _))
      .getOrElse(container.selectAll(tag).data(newData))

    enter(element.enter().append(tag))
    exit(element.exit())
  }
  def draw() {
    drawCall(container.selectAll(tag))
  }

  def enter(appended: Selection[T]) {}
  def exit(selection: Selection[T]) { selection.remove() }

  def drawCall(selection: Selection[T]) {}
}

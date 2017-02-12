package frontend.graphview

import org.scalajs.dom.console
import scalajs.js
import org.scalajs.dom
import org.scalajs.d3v4._
import mhtml._

abstract class DataSelection[T](
  val container: Selection[dom.EventTarget],
  val tag: String,
  keyFunction: Option[T => Any] = None
) {
  // var data: js.Array[T] = js.Array[T]()
  final def nodes = container.selectAll[T](tag)

  def update(newData: js.Array[T]) {
    // data = newData

    val element = keyFunction
      .map(nodes.data(newData, _))
      .getOrElse(nodes.data(newData))

    enter(element.enter().append(tag))
    exit(element.exit())
  }
  final def draw() { drawCall(nodes) }

  def enter(appended: Selection[T]) {}
  def exit(selection: Selection[T]) { selection.remove() }

  def drawCall(selection: Selection[T]) {}
}

abstract class RxDataSelection[T](
  val container: Selection[dom.EventTarget],
  val tag: String,
  val rxData: Rx[js.Array[T]],
  keyFunction: Option[T => Any] = None, //TODO: js.UndefOr to always pass it as second argument to data()
  autoDraw: Boolean = false
) {

  final def nodes = container.selectAll[T](tag)

  rxData.foreach { data =>
    val element = keyFunction
      .map(kf => nodes.data(data, kf))
      .getOrElse(nodes.data(data))

    val entered = element.enter()
    val appen = entered.append(tag)
    enter(appen)
    exit(element.exit())

    if (autoDraw) draw()
  }

  final def draw() { drawCall(nodes) }

  def enter(appended: Selection[T]) {}
  def exit(selection: Selection[T]) { selection.remove() }
  def drawCall(selection: Selection[T]) {}
}

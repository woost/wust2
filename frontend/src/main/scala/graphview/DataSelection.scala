package frontend.graphview

import org.scalajs.dom.console
import scalajs.js
import org.scalajs.dom
import org.scalajs.d3v4._
import mhtml._

trait DataComponent[T] {
  val tag: String
  val key: T => Any //TODO undefor?
  def enter(appended: Selection[T]) {}
  def exit(selection: Selection[T]) { selection.remove() }
  def draw(selection: Selection[T]) {}
}

class SelectData[T](component: DataComponent[T], container: Selection[dom.EventTarget]) {
  def nodes = container.selectAll[T](component.tag)
  def draw(): Unit = component.draw(nodes)
  def update(data: js.Array[T]): Unit = {
    val element = nodes.data(data, component.key)
    component.enter(element.enter().append(component.tag))
    component.exit(element.exit())
  }
}

object SelectData {
  def apply[T](component: DataComponent[T])(container: Selection[dom.EventTarget]) = new SelectData(component, container)
  def rx[T](component: DataComponent[T], rxData: Rx[js.Array[T]])(container: Selection[dom.EventTarget]): SelectData[T] = {
    val select = new SelectData(component, container)
    rxData.foreach(select.update)
    select
  }
  def autoRx[T](component: DataComponent[T], rxData: Rx[js.Array[T]])(container: Selection[dom.EventTarget]): SelectData[T] = {
    val select = new SelectData(component, container)
    rxData.foreach { data =>
      select.update(data)
      select.draw()
    }
    select
  }
}

package wust.frontend.views.graphview

import org.scalajs.d3v4._
import org.scalajs.dom
import rx._

import scala.scalajs.js

trait DataSelection[T] {
  val tag: String
  def enterAppend(selection: Selection[T]):Unit = {}
  def enter(selection: Enter[T]):Unit = { enterAppend(selection.append(tag)) }
  def update(selection: Selection[T]):Unit = {}
  def exit(selection: Selection[T]):Unit = { selection.remove() }
  def draw(selection: Selection[T]):Unit = {}
}

class WithKey[T](f: T => Any) extends (T => Any) {
  def apply(o: T) = f(o)
}

class SelectData[T: WithKey](component: DataSelection[T], val container: Selection[dom.EventTarget]) {
  private val keyFunction = implicitly[WithKey[T]]
  private def nodes = container.selectAll[T](component.tag)

  def draw(): Unit = component.draw(nodes)
  def update(data: js.Array[T]): Unit = {
    val element = nodes.data(data, keyFunction)
    component.enter(element.enter())
    component.update(nodes)
    component.exit(element.exit())
  }
}

object SelectData {
  def apply[T: WithKey](component: DataSelection[T])(container: Selection[dom.EventTarget]) = new SelectData(component, container)
   def rx[T: WithKey](component: DataSelection[T], rxData: Rx[js.Array[T]])(container: Selection[dom.EventTarget])(implicit ctx: Ctx.Owner): SelectData[T] = {
     val select = new SelectData(component, container)
     rxData.foreach(select.update)
     select
   }
   def rxDraw[T: WithKey](component: DataSelection[T], rxData: Rx[js.Array[T]])(container: Selection[dom.EventTarget])(implicit ctx: Ctx.Owner): SelectData[T] = {
     val select = new SelectData(component, container)
     rxData.foreach { data =>
       select.update(data)
       select.draw()
     }
     select
   }
}

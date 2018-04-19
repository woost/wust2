package wust.utilWeb.views

import outwatch.dom.VNode
import wust.utilWeb.GlobalState

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import rx._

//TODO: better no oop for views, have a function that maps a string to a view/function?
trait View {
  def apply(state:GlobalState)(implicit ctx: Ctx.Owner):VNode //TODO: def apply(implicit state:GlobalState):VNode
  val key:String
  val displayName:String
  //TODO: icon

  //TODO: equals method based on key?
}

object View {
  //TODO better
  var list: Seq[View] =
    ChatView ::
    Nil

  def default = list.head

  def getString(key: String): View = {
    val viewMap = (list.map(v => v.key -> v)(breakOut): Map[String,View]).withDefaultValue(default) //TODO if list not a var, can be value
    val splitted = key.split(TiledView.separator)
    if (splitted.size == 1) {
      viewMap(splitted(0))
    } else {
      new TiledView(splitted.map(viewMap)(breakOut))
    }
  }
}



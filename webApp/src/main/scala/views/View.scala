package wust.webApp.views

import outwatch.dom.VNode
import wust.webApp.GlobalState

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import rx._
import wust.webApp.views.graphview.GraphView

//TODO: better no oop for views, have a function that maps a string to a view/function?
trait View {
  def apply(state:GlobalState)(implicit ctx: Ctx.Owner):VNode //TODO: def apply(implicit state:GlobalState):VNode
  val key:String
  val displayName:String
}

object View {
  val list: Seq[View] =
    ChatView ::
      LoginView ::
      // UserSettingsView ::
      new GraphView() ::
      // AvatarView ::
      Nil


  private val viewMap:Map[String,View] = (list.map(v => v.key -> v)(breakOut): Map[String,View]).withDefaultValue(default)

  def default = list.head

  def fromString(key: String): View = {
    val splitted = key.split(TiledView.separator)
    if (splitted.length == 1) {
      viewMap(splitted(0))
    } else {
      new TiledView(splitted.map(viewMap)(breakOut))
    }
  }
}



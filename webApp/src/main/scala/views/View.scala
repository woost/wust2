package wust.webApp.views

import outwatch.dom.VNode
import wust.webApp.GlobalState

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import rx._
import wust.webApp.views.graphview.GraphView
import cats.data.NonEmptyList

//TODO: better no oop for views, have a function that maps a string to a view/function?
trait View {
  def apply(state:GlobalState)(implicit ctx: Ctx.Owner):VNode //TODO: def apply(implicit state:GlobalState):VNode
  val key:String
  val displayName:String

  //TODO this is needed for tracking content view and deciding whether to show the new group button in mainview
  def innerViews: Seq[View] = Seq(this)
}

object View {
  //TODO: can GraphView be an object?
  val contentList: NonEmptyList[View] = NonEmptyList(ChatView, new GraphView :: Nil)

  val list: List[View] =
      contentList.toList :::
      LoginView ::
      // UserSettingsView ::
      // AvatarView ::
      Nil


  val viewMap: Map[String,View] = (list.map(v => v.key -> v)(breakOut): Map[String,View]).withDefaultValue(default)

  def default = new TiledView(ViewOperator.Auto, contentList)
}

sealed trait ViewOperator {
  val separator: String
}
object ViewOperator {
  case object Row extends ViewOperator { override val separator = "|" }
  case object Column extends ViewOperator { override val separator = "/" }
  case object Auto extends ViewOperator { override val separator = "," }

  val fromString: PartialFunction[String, ViewOperator] = {
    case Row.separator => Row
    case Column.separator => Column
    case Auto.separator => Auto
  }
}

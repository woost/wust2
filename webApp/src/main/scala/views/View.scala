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
}

object View {
  val list: Seq[View] =
      ChatView ::
      new GraphView ::
      LoginView ::
      // UserSettingsView ::
      // AvatarView ::
      Nil


  val viewMap:Map[String,View] = (list.map(v => v.key -> v)(breakOut): Map[String,View]).withDefaultValue(default)

  def default = new TiledView(ViewOperator.Auto, NonEmptyList(list.head, list.tail.head :: Nil))
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

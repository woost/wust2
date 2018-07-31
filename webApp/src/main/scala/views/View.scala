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
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode //TODO: def apply(implicit state:GlobalState):VNode
  val viewKey: String
  val displayName: String

  def isContent: Boolean
}

object View {

  val list: List[View] =
    ChatView ::
      KanbanView ::
      GraphView ::
      LoginView ::
      SignupView ::
      NewGroupView ::
      UserSettingsView ::
      // AvatarView ::
      Nil

  val contentList: List[View] = list.filter(_.isContent)

  val viewMap: Map[String, View] = list.map(v => v.viewKey -> v)(breakOut)
  def default = ChatView // new TiledView(ViewOperator.Optional, contentList)
}

sealed trait ViewOperator {
  val separator: String
}
object ViewOperator {
  case object Row extends ViewOperator { override val separator = "|" }
  case object Column extends ViewOperator { override val separator = "/" }
  case object Auto extends ViewOperator { override val separator = "," }
  case object Optional extends ViewOperator { override val separator = "?" }

  val fromString: PartialFunction[String, ViewOperator] = {
    case Row.separator      => Row
    case Column.separator   => Column
    case Auto.separator     => Auto
    case Optional.separator => Optional
  }
}

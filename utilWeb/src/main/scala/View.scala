package wust.utilWeb.views

import outwatch.dom.VNode
import wust.utilWeb.GlobalState

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global
import rx._

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

  val fromString: Map[String,View] = {
    val map:Map[String,View] = list.map(v => v.key -> v)(breakOut)
    map.withDefaultValue(default)
  }
}



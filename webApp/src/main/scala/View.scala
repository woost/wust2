package wust.webApp.views

import outwatch.dom.VNode
import wust.webApp.GlobalState
import wust.webApp.views.graphview.GraphView

import collection.breakOut
import scala.concurrent.ExecutionContext.Implicits.global

trait View {
  def apply(state:GlobalState):VNode
  val key:String
  val displayName:String
  //TODO: icon

  //TODO: equals method based on key?
}

object View {
  val list:Seq[View] = (
    ChatView ::
      //    BoardView ::
      //    MyBoardView ::
         ArticleView ::
      UserSettingsView ::
      //    CodeView ::
      //    ListView ::
      new GraphView() ::
      TestView ::
      Nil
    )

  def default = list.head

  val fromString: Map[String,View] = {
    val map:Map[String,View] = list.map(v => v.key -> v)(breakOut)
    map.withDefaultValue(default)
  }
}



package wust.frontend.views

import outwatch.dom.VNode
import wust.frontend.GlobalState
import wust.frontend.views.graphview.GraphView

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



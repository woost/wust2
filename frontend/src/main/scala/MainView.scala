package frontend

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._

import mhtml._
import scala.xml.Node
import autowire._
import boopickle.Default._

import graph._
import graphview.GraphView

object MainView {
  sealed trait Tab
  object Tab {
    case object Graph extends Tab
    case object List extends Tab
  }

  private val tabMode: Var[Tab] = Var(Tab.Graph)

  def component(state: GlobalState.type) =
    <div fontFamily="sans-serif">
      <button onclick={ (_: Event) => Client.auth.register("hans", "***").call(); () }>register</button>
      <button onclick={ (_: Event) => Client.login(api.PasswordAuth("hans", "***")); () }>login</button>
      <button onclick={ (_: Event) => Client.logout(); () }>logout</button>
      <button onclick={ (_: Event) => tabMode.update(_ => Tab.Graph); GraphView.init(state.graph); () }>graph</button>
      <button onclick={ (_: Event) => tabMode.update(_ => Tab.List); () }>list</button>
      {
        tabMode.map {
          case Tab.Graph => GraphView.component
          case Tab.List => ListView.component(state.graph)
        }
      }
      <div style="position:fixed; width:100%; bottom:0; left:0; padding:5px; background:#FFF; borderTop:1px; solid #DDD">
        { AddPostForm.component(state.graph, state.focusedPost) }
      </div>
    </div>
}

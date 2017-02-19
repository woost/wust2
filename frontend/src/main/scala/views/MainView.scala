package frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._

import mhtml._
import scala.xml.Node
import autowire._
import boopickle.Default._

import frontend.{GlobalState, Client}
import graph._
import graphview.GraphView

object MainView {
  sealed trait Tab
  object Tab {
    case object Graph extends Tab
    case object List extends Tab
  }

  private val tabMode: Var[Tab] = Var(Tab.Graph)

  def component(state: GlobalState) =
    <div style="font-family:sans-serif">
      <button onclick={ (_: Event) => Client.auth.register("hans", "***").call(); () }>register</button>
      <button onclick={ (_: Event) => Client.login(api.PasswordAuth("hans", "***")); () }>login</button>
      <button onclick={ (_: Event) => Client.logout(); () }>logout</button>
      <button onclick={ (_: Event) => tabMode := Tab.Graph; () }>graph</button>
      <button onclick={ (_: Event) => tabMode := Tab.List; () }>list</button>
      <div style={ tabMode.map(m => if (m == Tab.Graph) "display:block" else "display:none") }>
        { GraphView.component(state) }
      </div>
      <div style={ tabMode.map(m => if (m == Tab.List) "display:block" else "display:none") }>
        { ListView.component(state) }
      </div>
      <div style="position:fixed; width:100%; bottom:0; left:0; padding:5px; background:#f7f7f7; border-top:1px solid #DDD">
        { AddPostForm.component(state) }
      </div>
    </div>
}

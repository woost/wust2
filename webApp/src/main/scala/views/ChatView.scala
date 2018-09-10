package wust.webApp.views

import cats.effect.IO
import outwatch.dom._
import outwatch.dom.dsl._
import wust.sdk.NodeColor._
import wust.util._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.ThreadView._

import scala.collection.breakOut

object ChatView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Rx {
        val page = state.page()
        inputField(state, page.parentIdSet, Handler.create[Unit].unsafeRunSync, false)
      },
      Rx {
        val graph = state.graph()
        graph.nodes.toSeq.map(_.str).map(node => div(
          state.user.map(_.toString) 
        ))
      }
    )
  }
}

package wust.webApp

import fontAwesome._
import fontAwesome.freeSolid._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.AuthUser
import wust.graph._
import wust.ids._
import wust.sdk.ChangesHistory
import wust.sdk.NodeColor._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components._
import wust.webApp.views._

import scala.scalajs.js
import scala.scalajs.js.Date

object BreadCrumbs {
  import MainViewParts._

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "breadcrumbs",
      state.nodeAncestorsHierarchy.map(_.map {
        case (level, nodeSeq) =>
          span(
            cls := "breadcrumb",
            nodeSeq.map(n => nodeTag(state, n)(cursor.pointer)): Seq[VNode],
            span("/", cls := "divider"),
          )
      }.toSeq.reverse),
    )
  }
}

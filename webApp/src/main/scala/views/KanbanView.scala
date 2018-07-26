package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.sdk.NodeColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import Elements._
import colorado.RGB

object KanbanView extends View {
  override val key = "kanban"
  override val displayName = "Kanban"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      Styles.flex,
      alignItems.flexStart,
      flexWrap.wrap,
      Rx {
        val graph = state.graphContent()
        val forest = graph.filter(nid => graph.nodesById(nid).isInstanceOf[Node.Content]).redundantForest

        forest.map(tree => renderTree(state, tree)(ctx)(
          margin := "0px 5px",
          boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)",
        ))
      }.toObservable
    )
  }

  private def renderTree(state: GlobalState, tree:Tree)(implicit ctx: Ctx.Owner):VNode = {
    div(
      borderRadius := "3px",
      tree match {
        case Tree.Parent(node, children) =>
          val rendered = Rendered.renderNodeData(node.data, maxLength = Some(100))
          VDomModifier(
            padding := "5px",
            color := "white",
            fontWeight.bold,
            fontSize.large,
            backgroundColor := RGB("#ff7a8e").hcl.copy(h = hue(tree.node.id)).toHex,
            boxShadow := "0px 1px 0px 1px rgba(99,99,99,0.45)",
            rendered( children.map(t => renderTree(state, t)(ctx)(marginTop := "8px")))
          )
        case Tree.Leaf(node) =>
          val rendered = nodeCardCompact(state, node)
          rendered(
            fontSize.medium,
            width := "200px"
          )
      }
    )
  }
}

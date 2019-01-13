package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

/*
 * This view list all properties or attributes of a node.
 * It is meant as the main view for editing properties or getting a detailed overview of a single node.
 */
object DetailView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    @inline def renderAsTag(tags: Seq[Node])(implicit ctx: Ctx.Owner) = {
      div(
        if(tags.nonEmpty) tags.map(t => nodeTag(state, t, pageOnClick = true))
        else "-"
      )
    }

    // View elements
    val list = div(cls := "ui list")
    val item = div(cls := "item")
    val content = div(cls := "content")
    val header = div(cls := "header")
    val description = div(cls := "description")

    div(
      overflow.auto,
      padding := "10px",

      Rx {
        // Data
        state.page().parentId map { subjectId =>
          val graph = state.graph()
          val subjectIdx = graph.idToIdx(subjectId)
          val subject = graph.nodes(subjectIdx)
          val tags = graph.tagParentsIdx(subjectIdx).map(graph.nodes)
          val properties = graph.propertiesEdgeIdx(subjectIdx).map(eidx => graph.edges(eidx).asInstanceOf[Edge.LabeledProperty]).map(e => (e.data.key, graph.nodesById(e.targetId)))

          val parents = graph.parentsIdx(subjectIdx).map(graph.nodes)
          val children = graph.childrenIdx(subjectIdx).map(graph.nodes)

          // TODO: Make all elements configurable
          list(
            item(
              content(
                header("Data"),
                description(subject.data.str)
              ),
              content(
                header("Role"),
                description(subject.role.toString)
              ),
              content(
                header("Access"),
                description(subject.meta.accessLevel.str)
              ),
              content(
                header("Properties"),
                description(
                  if(properties.nonEmpty) properties.map { case (propertyKey: String, propertyValue: Node) =>
                    Components.propertyTag(state, propertyKey, propertyValue)
                  }
                  else "-"
                )
              ),
              content(
                header("Tags"),
                description(renderAsTag(tags))
              ),
              content(
                header("Parents"),
                description(description(renderAsTag(parents)))
              ),
              content(
                header("Children"),
                description(description(children.map(c => nodeCard(c)(display.inlineBlock, marginTop := "5px", marginRight := "10px"))))
              ),
            )
          )
        },
      }
    )
  }
}


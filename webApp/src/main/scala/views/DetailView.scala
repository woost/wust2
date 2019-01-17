package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph.Edge.LabeledProperty
import wust.graph._
import wust.ids.{NodeData, NodeRole}
import wust.webApp.{Icons, Permission}
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
          val accessLevel = Permission.resolveInherited(graph, subjectId)

          val tags = graph.tagParentsIdx(subjectIdx).map(graph.nodes)
          @inline def numTags = tags.length

          val properties = graph.propertyPairIdx(subjectIdx)
          @inline def numProperties = properties.length

          val parents = graph.parentsIdx(subjectIdx).map(graph.nodes)
          @inline def numParents = parents.length

          @inline def numChildren = graph.childrenIdx.sliceLength(subjectIdx)

          // TODO: Make all elements configurable
          list(
            item(
              content(
                header("Data"),
                description(subject.data.str)
              ),
              content(
                header("Role"),
                description(
                  subject.role match {
                    case NodeRole.Task => Icons.task
                    case NodeRole.Message => Icons.conversation
                    case _ => ""
                  },
                  subject.role.toString
                )
              ),
              content(
                header(s"Access (${accessLevel.value})"),
                description(
                  accessLevel.icon,
                  accessLevel.description
                )
              ),
              content(
                header(s"Properties ($numProperties)"),
                description(
                  if(properties.nonEmpty) properties.map { case (propertyKey: LabeledProperty, propertyValue: Node) =>
                    Components.propertyTag(state, propertyKey, propertyValue)
                  } else "-"
                )
              ),
              content(
                header(s"Tags ($numTags)"),
                description(renderAsTag(tags))
              ),
              content(
                header(s"Parents ($numParents)"),
                description(description(renderAsTag(parents)))
              ),
              content(
                header(s"Children ($numChildren)"),
              ),
            )
          )
        },
      }
    )
  }
}


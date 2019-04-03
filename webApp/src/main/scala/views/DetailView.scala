package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole, View}
import wust.sdk.{BaseColors, NodeColor}
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
    @inline def renderAsRemovableTag(tags: Seq[Node], taggedNodeId: NodeId)(implicit ctx: Ctx.Owner) = {
      div(
        if(tags.nonEmpty) tags.map(tag => removableNodeTag(state, tag, taggedNodeId))
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
          val roleChildrenCounts = graph.topLevelRoleStats(state.user().id, subjectId :: Nil)

          list(
            item(
              content( // TODO: Configurable role (changeable on click)
                header("Role"),
                description(
                  Elements.icon(subject.role match {
                    case NodeRole.Task => Icons.task
                    case NodeRole.Message => Icons.conversation
                    case _ => ""
                  })(marginRight := "5px"),
                  subject.role.toString
                )
              ),
//              content( // TODO: Configurable role (changeable on click)
//                header("Role"),
//                description(
//                  ConvertSelection.all.map { convert => ConvertSelection.convertRoleSubItem(state, subject.asInstanceOf[Node.Content], convert.role, convert.icon) }
//                )
//              ),
              content( // TODO: Configurable access level (changeable on click)
                header(s"Access (${accessLevel.value})"),
                description(
                  Elements.icon(accessLevel.icon)(marginRight := "5px"),
                  accessLevel.description
                )
              ),
              content( // TODO: Configurable property value (changeable on click)
                header(s"Properties ($numProperties)"),
                description(
                  if(properties.nonEmpty) properties.map { case (propertyKey: Edge.LabeledProperty, propertyValue: Node) =>
                    Components.removablePropertyTag(state, propertyKey, propertyValue)
                  } else "-"
                )
              ),
              content( // TODO: Configurable tags (adding by autocomplete)
                header(s"Tags ($numTags)"),
                description(renderAsRemovableTag(tags, subjectId))
              ),
              content( // TODO: Configurable parents (adding by autocomplete)
                header(s"Parents ($numParents)"),
                description(renderAsTag(parents))
              ),
              content(
                header(s"Children ($numChildren)"),
                description(
                  roleChildrenCounts.roles.collect{
                    case RoleStat(NodeRole.Message, count, _) =>
                      div(
                        Styles.flex,
                        div(cls := "fa-fw", Icons.conversation),
                        div(count, marginLeft := "0.5em"),
                        onClick foreach {
                          state.urlConfig.update(_.focus(View.Conversation))
                        },
                        cursor.pointer,
                      )
                    case RoleStat(NodeRole.Task, count, _) =>
                      div(
                        Styles.flex,
                        div(cls := "fa-fw", Icons.tasks),
                        div(count, marginLeft := "0.5em"),
                        onClick foreach {
                          state.urlConfig.update(_.focus(View.Tasks))
                        },
                        cursor.pointer,
                      )
                  }
                )
              ),
              content(
                header("Content"),
                description(subject.data.str)
              ),
            )
          )
        }
      }
    )
  }
}


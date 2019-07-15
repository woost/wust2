package wust.webApp.views

import wust.css.ZIndex
import outwatch.dom.helpers.EmitterBuilder
import outwatch.dom._
import outwatch.dom.dsl._
import monix.reactive.Observer
import rx.{Ctx, Rx, Var}
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, ModalConfig, Ownable, UI}
import wust.css.Styles
import wust.graph.{Edge, GraphChanges, Node}
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.webApp.Icons
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{FocusPreference, GlobalState, GraphChangesAutomation}
import wust.webApp.views.Components._
import wust.webUtil.Elements
import wust.util.StringOps

import scala.collection.breakOut

// Offers methods for rendering components for the GraphChangesAutomation.

object GraphChangesAutomationUI {

  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(focusedId: NodeId, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): ModalConfig = {
    val header: VDomModifier = Rx {
      GlobalState.rawGraph().nodesById(focusedId).map { node =>
        Modal.defaultHeader( node, "Automation", Icons.automate)
      }
    }

    val newTemplateButton = button(
      cls := "ui button",
      cursor.pointer,

      onClick.mapTo {
        val name = GlobalState.rawGraph.now.nodesById(focusedId).fold("")(node => s" in: ${StringOps.trimToMaxLength(node.str, 20)}")
        val templateNode = Node.MarkdownTask("Template" + name)
        GraphChanges(addEdges = Array(Edge.Child(ParentId(focusedId), ChildId(templateNode.id)), Edge.Automated(focusedId, TemplateId(templateNode.id))), addNodes = Array(templateNode))
      } --> GlobalState.eventProcessor.changes,
    )

    val templatesRx = Rx {
      GlobalState.graph().templateNodes(GlobalState.graph().idToIdxOrThrow(focusedId))
    }

    // close the current sidebar, because we are reusing it here.
    GlobalState.rightSidebarNode() = None

    val showHelp = Var(false)
    val description: VDomModifier = div(
      Styles.flex,
      justifyContent.spaceBetween,

      height := "600px",
      onClick(false) --> showHelp,
      position.relative,

      Rx {
        val graph = GlobalState.rawGraph()
        val templates = templatesRx()

        div(
          padding := "10px",
          Styles.growFull,
          overflowY.auto,

          if (templates.isEmpty) b("This node is currently not automated.", alignSelf.flexStart)
          else VDomModifier(
            div(
              Styles.flex,
              justifyContent.spaceBetween,
              div(
                b("This node has active automation templates:"),
                div(fontSize.xSmall, "Each will be applied to every new child of this node."),
                marginBottom := "10px",
              ),

              div("Help", textDecoration := "underline", cursor.pointer, onClick.stopPropagation foreach showHelp.update(!_)),
            ),

            div(
              padding := "10px",
              Styles.flex,
              alignItems.center,
              b(fontSize.small, "Drag Users to assign them:", color.gray, marginRight := "5px"),
              SharedViewElements.channelMembers( GlobalState.page.now.parentId.get),
            )
          ),

          DragComponents.registerDragContainer,

          div(
            Styles.flex,
            flexDirection.column,
            alignItems.center,
            padding := "0 0 10px 10px",

            showHelp map {
              case true => div(
                overflow.auto,
                onClick.stopPropagation --> Observer.empty,
                zIndex := ZIndex.toast,
                position.absolute,
                background := "white",
                boxShadow := "0px 0px 3px 0px rgba(0, 0, 0, 0.32)",
                borderRadius := "4px",
                padding := "10px",
                top := "40px",
                right := "10px",
                height := "85%",
                width := "85%",

                h4("Help", color.gray),

                b("Automation Templates"),
                p("Each automation template in the list on the left hand side, is triggered whenever a new child is added to this node. By default, each of these automation templates refers to the new child node (if it is of the same kind like task, project, message or note). To make the template refer to a different node, you can add a template reference. This reference points to another template. A template with a template reference will match any node within the new child node, that was derived by the template reference."),
                p("If the automation triggers, it will walk through all nodes within the new child node and match them against the templates. Template references are resolved, and missing information will be added and updated."),

                b("Variables:"),
                p("You may use variables ${woost...} within your templates in order to build dynamic content:"),
                ul(
                  li(i("${woost.parent}"), " The parent of this node."),
                  li(i("${woost.field.<name>}"), " A custom field of this node, where the key is the name of the property."),
                  li(i("${woost.reverseField}"), " Points from a custom field to the owner of this field. Reverse direction of field."),
                  li(i("${woost.assignee}"), " Reference the assigend user of this node."),
                  li(i("${woost.original}"), " Reference the current node before renaming."),
                  li(i("${woost.myself}"), " The currently logged-in user. That is you!"),
                ),

                p("Each variable is resolved relative to the position of the node which uses this variables at the time the automation runs. You can combine variables to reference nodes via a whole path. For example: ${woost.parent.field.name}, ${woost.reverseField.parent}, or ${woost.parent.parent}.")
              )
              case false => VDomModifier.empty
            },

            Components.removeableList[Node](
              templates,
              multiObserver[Node](
                GlobalState.eventProcessor.changes.redirectMap { templateNode =>
                  val g = GlobalState.rawGraph.now
                  val existingParent = g.parentEdgeIdx(g.idToIdxOrThrow(templateNode.id)).find { edgeIdx =>
                    val edge = graph.edges(edgeIdx).as[Edge.Child]
                    edge.parentId == focusedId
                  }

                  GraphChanges(
                    addEdges = existingParent.map { edgeIdx =>
                      val edge = graph.edges(edgeIdx).as[Edge.Child]
                      edge.copy(data = edge.data.copy(deletedAt = Some(EpochMilli.now)))
                    }.toArray,
                    delEdges = Array(Edge.Automated(focusedId, TemplateId(templateNode.id)))
                  )
                },
                Sink.fromFunction(templateNode => GlobalState.rightSidebarNode.update(_.filter(_.nodeId != templateNode.id)))
              )
            )({ templateNode =>
                val propertySingle = PropertyData.Single(graph, graph.idToIdxOrThrow(templateNode.id))
                val isChildOfTemplate = Rx {
                  val g = GlobalState.rawGraph.now
                  g.parentsContains(templateNode.id)(focusedId)
                }

                div(
                  Components.nodeCard(templateNode, maxLength = Some(100)).apply(
                    padding := "3px",
                    width := "200px",
                    div(
                      Styles.flex,
                      flexWrap.wrap,

                      propertySingle.info.tags.map { tag =>
                        Components.removableNodeTag( tag, taggedNodeId = templateNode.id)
                      },

                      propertySingle.properties.map { property =>
                        property.values.map { value =>
                          Components.removableNodeCardProperty( value.edge, value.node)
                        }
                      },

                      {
                        val users: List[VNode] = propertySingle.info.assignedUsers.map { user =>
                          Components.removableUserAvatar( user, templateNode.id)
                        }(breakOut)

                        users match {
                          case head :: tail => head.apply(marginLeft := "auto") :: tail
                          case Nil => Nil
                        }
                      },
                    ),

                    DragItem.fromNodeRole(templateNode.id, templateNode.role).map(dragItem => DragComponents.drag(target = dragItem)),
                    Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, templateNode.id),
                  ).prepend(
                    b(color.gray, templateNode.role.toString)
                  ),

                  div(
                    margin := "2px 10px",
                    UI.checkboxEmitter(span(fontSize.xSmall, "Add template as child of this node", UI.popup := "If you check this box, this template will make any matched node a child of this focused node. If not checked, the template will not change the hierarchy of the matched node."), isChildOfTemplate).map { addAsChild =>
                      val edges = Array[Edge](Edge.Child(ParentId(focusedId), ChildId(templateNode.id)))
                      if (addAsChild) GraphChanges(addEdges = edges) else GraphChanges(delEdges = edges)
                    } --> GlobalState.eventProcessor.changes
                  )
                )
            }),

            newTemplateButton.apply(
              "+ Create a new Automation Template",
              alignSelf.flexStart,
              cls := "compact mini",
              margin := "30px 0 0 0"
            ),
          ),
        ),
      },

      position.relative, // needed for right sidebar
      RightSidebar(
        Rx { GlobalState.rightSidebarNode() },
        nodeId => GlobalState.rightSidebarNode() = nodeId.map(FocusPreference(_)),
        viewRender,
        openModifier = VDomModifier(overflow.auto, VDomModifier.ifTrue(BrowserDetect.isMobile)(marginLeft := "25px"))
      ) // overwrite left-margin of overlay sidebar in mobile
    )

    ModalConfig(header = header, description = description, contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for automation that opens the modal on click.
  def settingsButton(focusedId: NodeId, viewRender: ViewRenderLike, activeMod: VDomModifier = VDomModifier.empty, inactiveMod: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val accentColor = BaseColors.pageBg.copy(h = hue(focusedId)).toHex
    div(
      i(cls := "fa-fw", Icons.automate),

      Rx {
        val graph = GlobalState.rawGraph()
        val hasTemplates = graph.automatedEdgeIdx.sliceNonEmpty(graph.idToIdxOrThrow(focusedId))
        if (hasTemplates) VDomModifier(
          UI.tooltip("bottom center") := "Automation: active",
          color := accentColor,
          backgroundColor := "transparent",
          activeMod
        ) else VDomModifier(
          UI.tooltip("bottom center") := "Automation: inactive",
          inactiveMod
        )
      },
      cursor.pointer,
      onClick(Ownable(implicit ctx => modalConfig( focusedId, viewRender))) --> GlobalState.uiModalConfig
    )
  }

  // a settings item for automation to copy from a node
  def copyNodeItem(templateId: NodeId)(implicit ctx: Ctx.Owner): EmitterBuilder[(Node.Content, GraphChanges), VDomModifier] = EmitterBuilder.ofModifier { sink =>
    a(
      cls := "item",
      Elements.icon(Icons.copy),
      span("Copy Node"),
      cursor.pointer,
      onClick.foreach {
        GlobalState.rawGraph.now.idToIdxForeach(templateId) { templateIdx =>
          GlobalState.rawGraph.now.nodes(templateIdx) match {
            case templateNode: Node.Content =>
              val newData = templateNode.data match {
                case data: NodeData.EditableText => data.updateStr(s"Copy of '${data.str}'").getOrElse(data)
                case data => data
              }
              val newNode = templateNode.copy(id = NodeId.fresh, data = newData)
              val changes = GraphChangesAutomation.copySubGraphOfNode(GlobalState.userId.now, GlobalState.rawGraph.now, newNode, templateNodesIdx = Array(templateIdx))
              sink.onNext(newNode -> (changes merge GraphChanges(addNodes = Array(newNode))))
              ()
            case _ => ()
          }
        }
      }
    )
  }

  // a settings item for automation to resync with existing templates
  def resyncWithTemplatesItem(nodeId: NodeId)(implicit ctx: Ctx.Owner): EmitterBuilder[GraphChanges, VDomModifier] = EmitterBuilder.ofModifier { sink =>
    a(
      cls := "item",
      Elements.icon(Icons.resync),
      span("Re-Sync with templates"),
      cursor.pointer,
      onClick.foreach {
        val changes = GraphChangesAutomation.resyncWithTemplates(GlobalState.userId.now, GlobalState.rawGraph.now, nodeId)
        sink.onNext(changes)
        ()
      }
    )
  }
}

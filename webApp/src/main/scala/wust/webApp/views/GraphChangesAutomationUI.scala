package wust.webApp.views

import outwatch.dom.helpers.EmitterBuilder
import outwatch.dom._
import outwatch.dom.dsl._
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

import scala.collection.breakOut

// Offers methods for rendering components for the GraphChangesAutomation.

object GraphChangesAutomationUI {

  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(state: GlobalState, focusedId: NodeId, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): ModalConfig = {
    val header: VDomModifier = Rx {
      state.rawGraph().nodesById(focusedId).map { node =>
        Modal.defaultHeader(state, node, "Automation", Icons.automate)
      }
    }

    val selectedTemplate = Var[Option[FocusPreference]](None)

    val newTemplateButton = button(
      cls := "ui button",
      cursor.pointer,

      onClick.mapTo {
        val templateNode = Node.MarkdownTask("Template")
        GraphChanges(addEdges = Array(Edge.Child(ParentId(focusedId), ChildId(templateNode.id)), Edge.Automated(focusedId, TemplateId(templateNode.id))), addNodes = Array(templateNode))
      } --> state.eventProcessor.changes,
    )

    val templatesRx = Rx {
      state.graph().templateNodes(state.graph().idToIdxOrThrow(focusedId))
    }

    val description: VDomModifier = div(
      Styles.flex,
      justifyContent.spaceBetween,

      Rx {
        val graph = state.rawGraph()
        val templates = templatesRx()
        VDomModifier(
          height := "600px",

          div(
            padding := "10px",
            Styles.growFull,
            overflowY.auto,

            if (templates.isEmpty) b("This node is currently not automated.", alignSelf.flexStart)
            else VDomModifier(
              div(
                b("Active automation templates:"),
                div(fontSize.xSmall, "Each will be applied to every child of this node."),
                marginBottom := "10px",
              ),

              div(
                padding := "10px",
                Styles.flex,
                alignItems.center,
                b(fontSize.small, "Drag Users to assign them:", color.gray, marginRight := "5px"),
                SharedViewElements.channelMembers(state, state.page.now.parentId.get),
              )
            ),

            DragComponents.registerDragContainer(state),

            div(
              Styles.flex,
              flexDirection.column,
              alignItems.center,
              padding := "0 0 10px 10px",

              Components.removeableList[Node](
                templates,
                multiObserver[Node](
                  state.eventProcessor.changes.redirectMap { templateNode =>
                    val g = state.rawGraph.now
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
                  Sink.fromFunction(templateNode => selectedTemplate.update(_.filter(_.nodeId != templateNode.id)))
                )
              )({ templateNode =>
                  val propertySingle = PropertyData.Single(graph, graph.idToIdxOrThrow(templateNode.id))

                  Components.nodeCard(templateNode, maxLength = Some(100)).apply(
                    padding := "3px",
                    width := "200px",
                    div(
                      Styles.flex,
                      flexWrap.wrap,

                      propertySingle.info.tags.map { tag =>
                        Components.removableNodeTag(state, tag, taggedNodeId = templateNode.id)
                      },

                      propertySingle.properties.map { property =>
                        property.values.map { value =>
                          Components.removableNodeCardProperty(state, value.edge, value.node)
                        }
                      },

                      {
                        val users: List[VNode] = propertySingle.info.assignedUsers.map { user =>
                          Components.removableUserAvatar(state, user, templateNode.id)
                        }(breakOut)

                        users match {
                          case head :: tail => head.apply(marginLeft := "auto") :: tail
                          case Nil => Nil
                        }
                      },

                      state.rawGraph.map(g => VDomModifier.ifNot(g.parentsContains(templateNode.id)(focusedId))(i(color.gray, " * Template is not a direct child of the current node." ))),
                    ),

                    DragItem.fromNodeRole(templateNode.id, templateNode.role).map(dragItem => DragComponents.drag(target = dragItem)),
                    Components.sidebarNodeFocusMod(selectedTemplate, templateNode.id),
                  ).prepend(
                    b(color.gray, templateNode.role.toString)
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
        )
      },

      position.relative, // needed for right sidebar
      RightSidebar(
        state,
        Rx { selectedTemplate() },
        nodeId => selectedTemplate() = nodeId.map(FocusPreference(_)),
        viewRender,
        openModifier = VDomModifier(overflow.auto, VDomModifier.ifTrue(BrowserDetect.isMobile)(marginLeft := "25px"))
      ) // overwrite left-margin of overlay sidebar in mobile
    )

    ModalConfig(header = header, description = description, contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for automation that opens the modal on click.
  def settingsButton(state: GlobalState, focusedId: NodeId, viewRender: ViewRenderLike, activeMod: VDomModifier = VDomModifier.empty, inactiveMod: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val accentColor = BaseColors.pageBg.copy(h = hue(focusedId)).toHex
    div(
      i(cls := "fa-fw", Icons.automate),

      Rx {
        val graph = state.rawGraph()
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
      onClick(Ownable(implicit ctx => modalConfig(state, focusedId, viewRender))) --> state.uiModalConfig
    )
  }

  // a settings item for automation to copy from a node
  def copyNodeItem(state: GlobalState, templateId: NodeId)(implicit ctx: Ctx.Owner): EmitterBuilder[(Node.Content, GraphChanges), VDomModifier] = EmitterBuilder.ofModifier { sink =>
    a(
      cls := "item",
      Elements.icon(Icons.copy),
      span("Copy Node"),
      cursor.pointer,
      onClick.foreach {
        state.rawGraph.now.nodesById(templateId) match {
          case Some(templateNode: Node.Content) =>
            val newData = templateNode.data match {
              case data: NodeData.EditableText => data.updateStr(s"Copy of '${data.str}'").getOrElse(data)
              case data => data
            }
            val newNode = templateNode.copy(id = NodeId.fresh, data = newData)
            val changes = GraphChangesAutomation.copySubGraphOfNode(state.userId.now, state.rawGraph.now, newNode, templateNode)
            sink.onNext(newNode -> changes)
            ()
          case _ => ()
        }
      }
    )
  }
}

package wust.webApp.views

import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp.outwatchHelpers._
import rx.{Ctx, Rx, Var}
import wust.css.{CommonStyles, Styles}
import wust.graph.{Edge, GraphChanges, Node, Page}
import wust.ids._
import wust.webApp.dragdrop.DragItem
import wust.webApp.{Icons, Ownable, BrowserDetect}
import wust.webApp.state.{GlobalState, FocusPreference}

import scala.collection.breakOut

// Offers methods for rendering components for the GraphChangesAutomation.

object GraphChangesAutomationUI {

  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): UI.ModalConfig = {
    val header: VDomModifier = Rx {
      state.rawGraph().nodesByIdGet(focusedId).map { node =>
        UI.ModalConfig.defaultHeader(state, node, "Automation", Icons.automate)
      }
    }

    val selectedTemplate = Var[Option[FocusPreference]](None)

    val newTemplateButton = button(
      cls := "ui button",
      cursor.pointer,

      onClick.mapTo {
        val templateNode = Node.MarkdownTask("Template")
        GraphChanges(addEdges = Set(Edge.Child(ParentId(focusedId), ChildId(templateNode.id)), Edge.Automated(focusedId, TemplateId(templateNode.id))), addNodes = Set(templateNode))
      } --> state.eventProcessor.changes,
    )

    val description: VDomModifier = div(
      Styles.flex,
      justifyContent.spaceBetween,

      Rx {
        val graph = state.rawGraph()
        val templates = graph.templateNodes(graph.idToIdx(focusedId))
        if(templates.isEmpty) {
          VDomModifier(
            padding := "10px",
            Styles.flex,
            flexDirection.column,
            b("This node is currently not automated.", alignSelf.flexStart),
            newTemplateButton.apply(
              marginTop := "10px",
              alignSelf.center,
              "Create new Automation Template",
              cls := "primary"
            ),
          )
        } else {
          VDomModifier(
            height := "600px",

            div(
              padding := "10px",
              Styles.growFull,
              overflowY.auto,

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
                state.rawGraph.map(_.nodesByIdGet(state.page.now.parentId.get).map(PageHeader.channelMembers(state, _))),
              ),

              Components.registerDragContainer(state),

              div(
                Styles.flex,
                flexDirection.column,
                alignItems.center,
                padding := "0 0 10px 10px",

                Components.removeableList[Node](
                  templates,
                  state.eventProcessor.changes.redirectMap { templateNode =>
                    val g = state.rawGraph.now
                    val existingParent = g.parentEdgeIdx(g.idToIdxOrThrow(templateNode.id)).find { edgeIdx =>
                      val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Child]
                      edge.parentId == focusedId
                    }

                    GraphChanges(
                      addEdges = existingParent.map { edgeIdx =>
                        val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Child]
                        edge.copy(data = edge.data.copy(deletedAt = Some(EpochMilli.now)))
                      }.toSet,
                      delEdges = Set(Edge.Automated(focusedId, TemplateId(templateNode.id)))
                    )
                  },
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
                            Components.removablePropertyTag(state, value.edge, value.node)
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

                        state.rawGraph.map(g => VDomModifier.ifNot(g.parents(templateNode.id).contains(focusedId))(i(color.gray, " * Template is not a direct child of the current node." ))),
                      ),

                      DragItem.fromNodeRole(templateNode.id, templateNode.role).map(dragItem => Components.drag(target = dragItem)),
                      Components.sidebarNodeFocusMod(selectedTemplate, templateNode.id),
                    ).prepend(
                      b(color.gray, templateNode.role.toString)
                    )
                }),

                newTemplateButton.apply(
                  "+ Add Template",
                  cls := "compact mini",
                  margin := "10px 0 0 0"
                ),
              ),
            ),
          )
        }
      },

      position.relative, // needed for right sidebar
      RightSidebar(state, selectedTemplate, nodeId => selectedTemplate() = nodeId.map(FocusPreference(_)), openModifier = VDomModifier(overflow.auto, VDomModifier.ifTrue(BrowserDetect.isMobile)(marginLeft := "25px"))) // overwrite left-margin of overlay sidebar in mobile
    )

    UI.ModalConfig(header = header, description = description, contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for automation that opens the modal on click.
  def settingsButton(state: GlobalState, focusedId: NodeId, activeMod: VDomModifier = VDomModifier.empty, inactiveMod: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    div(
      i(cls := "fa-fw", Icons.automate),

      Rx {
        val graph = state.rawGraph()
        val templates = graph.templateNodes(graph.idToIdx(focusedId))
        if (templates.isEmpty) VDomModifier(
          UI.popup := "Automation: inactive",
          inactiveMod
        ) else VDomModifier(
          UI.popup := "Automation: active",
          color := CommonStyles.selectedNodesBgColorCSS,
          activeMod
        )
      },
      cursor.pointer,
      onClick(Ownable(implicit ctx => modalConfig(state, focusedId))) --> state.uiModalConfig
    )
  }
}

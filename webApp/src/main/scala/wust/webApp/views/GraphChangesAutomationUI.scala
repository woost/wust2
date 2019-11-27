package wust.webApp.views

import com.github.ghik.silencer.silent
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.ext.monix._
import outwatch.reactive._
import rx.{Ctx, Rx, Var}
import wust.css.{Styles, ZIndex}
import wust.graph.{Edge, GraphChanges, Node}
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util.StringOps
import wust.webApp.Icons
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{FeatureState, FocusPreference, FocusState, GlobalState, GraphChangesAutomation, TraverseState}
import wust.webUtil.outwatchHelpers._
import wust.webUtil._
import wust.webUtil.Elements._

import scala.collection.breakOut
import wust.facades.segment.Segment
import fontAwesome.freeSolid

// Offers methods for rendering components for the GraphChangesAutomation.

@silent("possible missing interpolator")
object GraphChangesAutomationUI {

  val createAutomationTemplateText = "Create Template"
  // returns the modal config for rendering a modal for configuring automation of the node `nodeId`.
  def modalConfig(focusState: FocusState, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): ModalConfig = {
    val focusedId = focusState.focusedId
    val traverseState = TraverseState(focusedId)

    val header: VDomModifier = Rx {
      GlobalState.rawGraph().nodesById(focusedId).map { node =>
        val automationTriggerDescription = node.role match {
          case NodeRole.Stage => " (triggered when a card is moved into or created in this column)"
          case NodeRole.Tag => " (triggered when a task is tagged)"
          case _ => ""
        }
        Modal.defaultHeader(node, s"Automation$automationTriggerDescription", Icons.automate)
      }
    }

    val newTemplateButton = button(
      cls := "ui button",
      cursor.pointer,

      onClick.stopPropagation.foreach { _ =>
        val name = GlobalState.rawGraph.now.nodesById(focusedId).fold("")(node => s" for **${StringOps.trimToMaxLength(node.str, 20)}**")
        val templateNode = Node.MarkdownTask("Template" + name)
        val changes = GraphChanges(
          addEdges = Array(
            Edge.Child(ParentId(focusedId), ChildId(templateNode.id)),
            Edge.Automated(focusedId, TemplateId(templateNode.id))),
          addNodes = Array(templateNode)
        )
        GlobalState.submitChanges(changes)
        FeatureState.use(Feature.CreateAutomationTemplate)
      }
    )

    val reuseExistingTemplate = Components.searchInGraph(GlobalState.rawGraph, "Reuse existing template", filter = node => GlobalState.rawGraph.now.isAutomationTemplate(GlobalState.rawGraph.now.idToIdxOrThrow(node.id))).map { nodeId =>
      GraphChanges(addEdges = Array(Edge.Automated(focusedId, TemplateId(nodeId))))
    } --> GlobalState.eventProcessor.changes

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
      onClick.use(false) --> showHelp,
      position.relative,

      Rx {
        val graph = GlobalState.rawGraph()
        val templates = templatesRx()

        div(
          padding := "20px",
          Styles.growFull,
          overflowY.auto,

          if (templates.isEmpty) span("No automations set up yet. Create a template to start.", alignSelf.flexStart, opacity := 0.5)
          else VDomModifier(
            div(
              Styles.flex,
              alignItems.flexStart,
              div(
                width := "300px",
                b("There are active automation templates."),
                div(fontSize.small, "A template describes how items will look like after the automation is applied. Click a template to see and change what's inside. An easy way to get started is to add subtasks to the template, close this window and execute the automation."),
                marginBottom := "10px",
                marginRight.auto,
              ),

              div(
                Styles.flex,
                alignItems.center,
                flexWrap.wrap,
                button("Documentation", cls := "ui blue basic compact button", onClickDefault.foreach{ showHelp.update(!_)}, margin := "5px"),
                FeedbackForm.supportChatButton.apply(cls := "compact basic", margin := "5px"),
              )
            ),

            div(
              padding := "10px 0px",
              Styles.flex,
              alignItems.center,
              span(fontSize.small, "Drag users to assign:", color.gray, marginRight := "5px"),
              SharedViewElements.channelMembers( GlobalState.page.now.parentId.get),
            )
          ),

          DragComponents.registerDragContainer,

          div(
            Styles.flex,
            flexDirection.column,
            alignItems.flexStart,

            showHelp map {
              case true => div(
                overflow.auto,
                onClick.stopPropagation.discard,
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
                p("On the left hand side, you can see all automation templates that will be triggered when something new is added into the currently focused node."),
                p("When the automation is triggered by the new child, each of theses templates is evaluated and the automation will make sure that the new child will look like the templates and have all of their properties. You can add new tasks to your cards, add custom fields, create new projects or kanban boards and much more."),
                p("By default, a template will refer to every newly added child if it has the same role (message, task, project or note). To make the template refer to an existing node within the child node, you can add a template reference. This reference points to another automation template. A template with a template reference will match any node within the new child node, that was derived by the referenced template."),

                b("Variables:"),
                p("You may use variables ${woost...} within your templates in order to build dynamic content:"),
                ul(
                  li(i("${woost.parent}"), " The parent of this node."),
                  li(i("${woost.child}"), " The child of this node."),
                  li(i("${woost.field.<name>}"), " A custom field of this node, where the key is the name of the property."),
                  li(i("${woost.reverseField}"), " Points from a custom field to the owner of this field. Reverse direction of field."),
                  li(i("${woost.assignee}"), " Reference the assigend user of this node."),
                  li(i("${woost.id}"), " Get the id of the current node."),
                  li(i("${woost.url}"), " Get the url of the current node."),
                  li(i("${woost.fileUrl}"), " Get the file-url of the current file node."),
                  li(i("""${woost.id("<id>")}"""), " Lookup a node via its id."),
                  li(i("${woost.original}"), " Reference the current node before renaming."),
                  li(i("${woost.myself}"), " The currently logged-in user. That is you!"),
                ),

                p("Each variable is resolved relative to the position of the node which uses this variables at the time the automation runs. You can combine variables to reference nodes via a whole path. For example: ${woost.parent.field.name}, ${woost.reverseField.parent}, ${woost.parent.parent}, ${woost.parent.id}."),

                p("You can mention the result of a variable via: $@{woost.parent.assignee}"),

                p("""Furthermore, you can handle how multiple variable results are joined. This is the case when you have multiple properties with the same name, e.g. "Participant: Peter" and "Participant: Lois". By default, a list of result will be joined with a comma as separator. You can define a custom separator string for multiple results with: join("<br/>"). Or you can as well join with a prefix and postfix for each element: join("<br />", "<b>", "</b>"). On the participant variable, this results in: "<b>Peter</b><br /><b>Lois</b>"."""),

                p("""If a variable has a syntax error, it will return '#NAME?' error. If a variable yields no results, it will return a '#REF!' error. In the latter case, you can provide a default value: ${woost.field.name.or("Unknown")}.""")
              )
              case false => VDomModifier.empty
            },

            Components.removeableList[Node](
              templates,
              SinkObserver.combine(
                SinkObserver.lift(GlobalState.eventProcessor.changes.contramap[Node] { templateNode =>
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
                }),
                SinkObserver.create[Node](templateNode => GlobalState.rightSidebarNode.update(_.filter(_.nodeId != templateNode.id)))
              )
            )({ templateNode =>
                val propertySingle = PropertyData.Single(graph, graph.idToIdxOrThrow(templateNode.id))
                val isChildOfTemplate = Rx {
                  val g = GlobalState.rawGraph.now
                  g.parentsContains(templateNode.id)(focusedId)
                }

                div(
                  Components.nodeCard(templateNode, maxLength = Some(100)).apply(
                    padding := "10px",
                    width := "200px",
                    div(
                      Styles.flex,
                      flexWrap.wrap,

                      propertySingle.info.tags.map { tag =>
                        Components.removableNodeTag( tag, taggedNodeId = templateNode.id)
                      },

                      propertySingle.properties.map { property =>
                        property.values.map { value =>
                          Components.nodeCardProperty(focusState, traverseState, value.edge, value.node)
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
                    Components.sidebarNodeFocusMod(templateNode.id, focusState),
                  )/*.prepend(
                    b(color.gray, templateNode.role.toString)
                  )*/,

                  div(
                    margin := "2px 10px",
                    UI.checkboxEmitter(span(fontSize.xSmall, "Add template as child of this node", UI.popup := "If you check this box, this template will make any matched node a child of this focused node. If not checked, the template will not change the hierarchy of the matched node."), isChildOfTemplate).map { addAsChild =>
                      val edges = Array[Edge](Edge.Child(ParentId(focusedId), ChildId(templateNode.id)))
                      if (addAsChild) GraphChanges(addEdges = edges) else GraphChanges(delEdges = edges)
                    } --> GlobalState.eventProcessor.changes
                  )
                )
            }),

            div(
              width := "100%",
              Styles.flex,
              justifyContent.spaceBetween,
              alignItems.center,
              margin := "30px 0 0 0",

              newTemplateButton.apply(
                // span(freeSolid.faPlus, marginRight := "0.5em"),
                s"$createAutomationTemplateText",
                alignSelf.flexStart,
                cls := "compact primary",
              ),

              div(
                fontSize.small,
                reuseExistingTemplate
              )
            )
          ),
        ),
      },

      position.relative, // needed for right sidebar
      RightSidebar(
        GlobalState.rightSidebarNode,
        focusPreference => GlobalState.rightSidebarNode() = focusPreference,
        viewRender,
        openModifier = VDomModifier(overflow.auto, VDomModifier.ifTrue(BrowserDetect.isMobile)(marginLeft := "25px"))
      ) // overwrite left-margin of overlay sidebar in mobile
    )

    ModalConfig(header = header, description = description, contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for automation that opens the modal on click.
  def settingsButton(
    focusedId: NodeId,
    viewRender: ViewRenderLike,
    activeMod: VDomModifier = VDomModifier.empty,
    inactiveMod: VDomModifier = VDomModifier.empty,
    tooltipDirection: String = "bottom center",
  )(implicit ctx: Ctx.Owner): VNode = {
    val accentColor = BaseColors.pageBg.copy(h = hue(focusedId)).toHex
    val hasTemplates = Rx {
      val graph = GlobalState.rawGraph()
      graph.automatedEdgeIdx.sliceNonEmpty(graph.idToIdxOrThrow(focusedId))
    }

    // This clearly defines how clicking items in this modal behave.
    val focusState = FocusState(
      view = View.Empty,
      contextParentId = focusedId,
      focusedId = focusedId,
      isNested = false,
      changeViewAction = view => (),
      contextParentIdAction = nodeId => GlobalState.focus(nodeId),
      itemIsFocused = nodeId => GlobalState.rightSidebarNode.map(_.exists(_.nodeId == nodeId)),
      onItemSingleClick = { focusPreference =>
        // toggle rightsidebar:
        val nextNode = if (GlobalState.rightSidebarNode.now.exists(_ == focusPreference)) None else Some(focusPreference)
        GlobalState.rightSidebarNode() = nextNode
      },
      onItemDoubleClick = nodeId => GlobalState.focus(nodeId),
    )


    div(
      i(cls := "fa-fw", Icons.automate),

      cursor.pointer,
      onClick.use(Ownable(implicit ctx => modalConfig( focusState, viewRender))) --> GlobalState.uiModalConfig,
      onClickDefault.foreach {
        Segment.trackEvent("Open Automation Modal")
      },

      Rx {
        if (hasTemplates()) VDomModifier(
          UI.tooltip(tooltipDirection) := "Automation: active",
          color := accentColor,
          backgroundColor := "transparent",
          activeMod
        ) else VDomModifier(
          UI.tooltip(tooltipDirection) := "Automation: inactive",
          inactiveMod
        )
      },
    )
  }

  // a settings item for automation to copy from a node
  def copyNodeItem(templateId: NodeId)(implicit ctx: Ctx.Owner): EmitterBuilder[(Node.Content, GraphChanges), VDomModifier] = EmitterBuilder.ofModifier { sink =>
    a(
      cls := "item",
      Elements.icon(Icons.copy),
      span("Duplicate Node"),
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
              val changes = GraphChangesAutomation.copySubGraphOfNode(GlobalState.userId.now, GlobalState.rawGraph.now, newNode, templateNodeIdxs = Array(templateIdx), isFullCopy = true)
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

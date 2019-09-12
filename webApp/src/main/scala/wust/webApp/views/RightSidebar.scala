package wust.webApp.views

import acyclic.file
import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.Colors
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webUtil.Elements
import wust.webUtil.BrowserDetect
import wust.webApp.views.SharedViewElements._
import wust.webApp.{ Icons, ItemProperties }
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Ownable, UI }
import com.github.ghik.silencer.silent

object RightSidebar {

  @inline def apply(viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner): VNode = apply(GlobalState.rightSidebarNode, nodeId => GlobalState.rightSidebarNode() = nodeId.map(FocusPreference(_)), viewRender: ViewRenderLike)
  def apply(focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit, viewRender: ViewRenderLike, openModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {

    val isFullscreen = Var(false)
    val focusHistory = Var(List.empty[NodeId])
    val focusFuture = Var(List.empty[NodeId])

    val toggleVar = Var(focusedNodeId.now.isDefined)
    var lastFocusPref = focusedNodeId.now
    focusedNodeId.triggerLater { opt =>
      toggleVar() = opt.isDefined
      opt match {
        case Some(newPref) =>
          lastFocusPref.foreach { pref =>
            focusHistory.update(list => pref.nodeId :: list.filter(_ != pref.nodeId))
            focusFuture() = Nil
          }
        case None =>
          focusHistory() = Nil
          focusFuture() = Nil
          isFullscreen() = false
      }
      lastFocusPref = opt
    }
    toggleVar.triggerLater(show => if (!show) parentIdAction(None))

    GenericSidebar.right(
      toggleVar,
      isFullscreen,
      config = Ownable { implicit ctx =>
        GenericSidebar.Config(
          openModifier = VDomModifier(focusedNodeId.map(_.map(content(_, parentIdAction, viewRender, isFullscreen, focusHistory, focusFuture, () => lastFocusPref = None))), openModifier)
        )
      }
    )
  }

  val propertiesAccordionText = "Properties & Custom Fields"
  val addCustomFieldText = "Add Custom Field"

  def content(focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit, viewRender: ViewRenderLike, isFullscreen: Var[Boolean], focusHistory: Var[List[NodeId]], focusFuture: Var[List[NodeId]], ignoreNextUpdate: () => Unit)(implicit ctx: Ctx.Owner): VNode = {
    val nodeStyle = PageStyle.ofNode(focusPref.nodeId)

    val sidebarHeader = div(
      opacity := 0.5,

      Styles.flex,
      justifyContent.spaceBetween,

      div(
        Styles.flex,
        alignItems.center,

        div(
          freeSolid.faAngleDoubleRight,
          color := "gray",
          cls := "fa-fw",
          fontSize.xLarge,
          cursor.pointer,
          onClick.use(None).foreach(parentIdAction)
        ),
        div(
          marginLeft := "5px",
          nodeBreadcrumbs(focusPref, parentIdAction, hideIfSingle = true),
        )
      ),

      div(
        Styles.flex,
        alignItems.center,

        div(
          freeSolid.faChevronLeft,
          focusHistory.map {
            case Nil => color := "gray"
            case _   => VDomModifier.empty
          },
          cls := "fa-fw",
          cursor.pointer,
          onClick.foreach {
            focusHistory.now match {
              case head :: rest =>
                ignoreNextUpdate()
                focusHistory() = rest
                focusFuture.update(focusPref.nodeId :: _)
                parentIdAction(Some(head))
              case _ =>
            }
          }
        ),
        div(
          freeSolid.faChevronRight,
          focusFuture.map {
            case Nil => color := "gray"
            case _   => VDomModifier.empty
          },
          cls := "fa-fw",
          cursor.pointer,
          onClick.foreach {
            focusFuture.now match {
              case head :: rest =>
                ignoreNextUpdate()
                focusFuture() = rest
                focusHistory.update(focusPref.nodeId :: _)
                parentIdAction(Some(head))
              case _ =>
            }
          }
        ),

        VDomModifier.ifNot(BrowserDetect.isMobile)(
          div(
            freeSolid.faCompress,
            cls := "fa-fw",
            cursor.pointer,
            onClick.foreach(isFullscreen.update(!_))
          )
        )
      )
    )

    def accordionEntry(title: VDomModifier, content: VDomModifier, active: Boolean): UI.AccordionEntry = {
      UI.AccordionEntry(
        title = VDomModifier(
          b(title),
          marginTop := "5px",
          Styles.flexStatic,
        ),
        content = VDomModifier(
          margin := "5px",
          padding := "0px",
          content
        ),
        active = active
      )
    }

    div(
      height := "100%",
      Styles.flex, // we need flex here because otherwise the height of this element is wrong - it overflows.
      flexDirection.column,
      color.black,

      sidebarHeader.apply(Styles.flexStatic),
      nodeContent(focusPref, parentIdAction).apply(Styles.flexStatic, overflowY.auto, maxHeight := "50%"),

      UI.accordion(
        content = Seq(
          accordionEntry(propertiesAccordionText, VDomModifier(
            nodeProperties(focusPref, parentIdAction),
            Styles.flexStatic,
          ), active = false),
          accordionEntry("Views", VDomModifier(
            height := "100%",
            viewContent(focusPref, parentIdAction, nodeStyle, viewRender),
          ), active = true),
        ),
        styles = "styled fluid",
        exclusive = false, //BrowserDetect.isMobile,
      ).apply(
          height := "100%",
          Styles.flex,
          flexDirection.column,
          justifyContent.flexStart,
          boxShadow := "none", //explicitly overwrite boxshadow from accordion.
        )
    )
  }
  private def viewContent(focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit, nodeStyle: PageStyle, viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner) = {
    val graph = GlobalState.rawGraph.now // this is per new focusPref, and ViewSwitcher just needs an initialvalue
    val initialView: View.Visible = graph.nodesById(focusPref.nodeId).flatMap(ViewHeuristic.bestView(graph, _, GlobalState.user.now.id)).getOrElse(View.Empty)
    val currentView: Var[View.Visible] = Var(initialView).imap(identity)(view => ViewHeuristic.visibleView(graph, focusPref.nodeId, view).getOrElse(View.Empty))

    currentView.triggerLater{ view =>
      view match {
        case View.Kanban => FeatureState.use(Feature.SwitchToKanbanInRightSidebar)
        case View.List   => FeatureState.use(Feature.SwitchToChecklistInRightSidebar)
        case View.Chat   => FeatureState.use(Feature.SwitchToChatInRightSidebar)
        case _           =>
      }
    }

    //TODO: really ugly, to widen the var for the viewswitcher :/
    val viewSwitcherVar: Var[View] = Var(currentView.now)
    currentView.triggerLater(viewSwitcherVar() = _)
    viewSwitcherVar.triggerLater(newView => ViewHeuristic.visibleView(graph, focusPref.nodeId, newView).foreach(currentView() = _))

    def focusState(view: View.Visible) = FocusState(
      view,
      focusPref.nodeId,
      focusPref.nodeId,
      isNested = true,
      viewAction = newView => ViewHeuristic.visibleView(graph, focusPref.nodeId, newView).foreach(currentView() = _),
      nodeId => parentIdAction(Some(nodeId))
    )

    VDomModifier(
      Styles.flex,
      flexDirection.column,
      margin := "0px", // overwrite accordion entry margin

      div(
        cls := "pageheader",
        backgroundColor := nodeStyle.pageBgColor,
        paddingTop := "10px", // to have some colored space above the tabs
        Styles.flexStatic,
        Styles.flex,
        alignItems.center,

        ViewSwitcher(focusPref.nodeId, viewSwitcherVar, focusPref.view.flatMap(ViewHeuristic.visibleView(graph, focusPref.nodeId, _))),
        UnreadComponents.activityButtons(focusPref.nodeId, modifiers = marginLeft := "10px") --> currentView,
      ),

      Rx {
        val view = currentView()
        viewRender(focusState(view), view).apply(
          Styles.growFull,
          flexGrow := 1,
        ).prepend(
            overflow.visible,
            backgroundColor := Colors.contentBg,
          )
      }
    )
  }

  private def nodeBreadcrumbs(focusedNodeId: FocusPreference, parentIdAction: Option[NodeId] => Unit, hideIfSingle: Boolean)(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      Rx {
        val page = GlobalState.page()
        page.parentId.map { parentId =>
          BreadCrumbs(
            GlobalState.rawGraph(),
            start = BreadCrumbs.EndPoint.Node(parentId, inclusive = false),
            end = BreadCrumbs.EndPoint.Node(focusedNodeId.nodeId),
            nodeId => parentIdAction(Some(nodeId)),
            hideIfSingle = hideIfSingle
          ).apply(paddingBottom := "3px")
        }
      }
    )
  }

  private def nodeContent(focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    val editMode = Var(false)

    val node = Rx {
      GlobalState.graph().nodesById(focusPref.nodeId)
    }

    val hasNotDeletedParents = Rx {
      GlobalState.graph().hasNotDeletedParents(focusPref.nodeId)
    }

    val buttonMods = VDomModifier(
      color := "gray",
      fontSize := "18px",
      padding := "12px 8px",
      cursor.pointer,
    )

    val zoomButton = div(
      Icons.zoom,
      buttonMods,
      onClick.foreach {
        GlobalState.focus(focusPref.nodeId)
        GlobalState.graph.now.nodesById(focusPref.nodeId).foreach { node =>
          node.role match {
            case NodeRole.Task    => FeatureState.use(Feature.ZoomIntoTask)
            case NodeRole.Message => FeatureState.use(Feature.ZoomIntoMessage)
            case NodeRole.Note    => FeatureState.use(Feature.ZoomIntoNote)
            case NodeRole.Project => FeatureState.use(Feature.ZoomIntoProject)
            case _                =>
          }
        }
      }
    )

    val deleteButton = Rx {
      VDomModifier.ifTrue(hasNotDeletedParents())(
        div(
          Icons.delete,
          buttonMods,
          onClick.stopPropagation.foreach { _ =>
            Elements.confirm("Delete this item?") {
              GlobalState.submitChanges(GraphChanges.deleteFromGraph(ChildId(focusPref.nodeId), GlobalState.graph.now))
              parentIdAction(None)
            }
          },
        )
      )
    }

    val nodeCard = Rx {
      node().map{ node =>
        Components.nodeCardEditable(node, editMode,
          contentInject = width := "100%" // pushes cancel button to the right
        ).apply(
          cls := "right-sidebar-node",

          Styles.flex,
          justifyContent.spaceBetween,

          fontSize := "20px",
          width := "100%",
          margin := "3px 3px 3px 3px",
          Styles.wordWrap,
          cls := "enable-text-selection",
          onClick.stopPropagation.use(true) --> editMode,

          UnreadComponents.readObserver(node.id)
        )
      }
    }

    div(
      div(
        Styles.flex,
        alignItems.flexStart,

        nodeCard,
        Rx {
          VDomModifier.ifNot(editMode())(
            zoomButton,
            deleteButton
          )
        }
      ),

      nodeAuthor(focusPref.nodeId),
    )
  }

  private def nodeAuthor(nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val authorship = Rx {
      val graph = GlobalState.graph()
      graph.idToIdxMap(nodeId) { idx =>
        val author = graph.nodeCreator(idx)
        val creationEpochMillis = graph.nodeCreated(idx)
        (author, creationEpochMillis)
      }
    }

    div(
      Styles.flex,
      justifyContent.flexEnd,

      authorship.map(_.map {
        case (author, creationEpochMillis) =>
          chatMessageHeader(author, creationEpochMillis, nodeId, author.map(smallAuthorAvatar)).apply(marginRight := "5px")
      }),
    )
  }

  private def nodeProperties(focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {

    val propertySingle = Rx {
      val graph = GlobalState.rawGraph()
      graph.idToIdxMap(focusPref.nodeId) { nodeIdx =>
        PropertyData.Single(graph, nodeIdx)
      }
    }
    def renderSplit(left: VDomModifier, right: VDomModifier) = div(
      Styles.flex,
      justifyContent.spaceBetween,
      div(
        left
      ),
      div(
        Styles.flex,
        justifyContent.flexEnd,
        right
      )
    )

    def createNewTag(str: String): Boolean = {
      val createdNode = Node.MarkdownTag(str)
      val change = GraphChanges.addNodeWithParent(createdNode, ParentId(GlobalState.page.now.parentId)) merge
        GraphChanges.connect(Edge.Child)(ParentId(createdNode.id), ChildId(focusPref.nodeId))
      GlobalState.submitChanges(change)
      true
    }

    def searchInput(placeholder: String, filter: Node => Boolean, createNew: String => Boolean = _ => false, showNotFound: Boolean = true) =
      Components.searchInGraph(GlobalState.rawGraph, placeholder = placeholder, filter = filter, showNotFound = showNotFound, createNew = createNew, inputModifiers = VDomModifier(
        width := "140px",
        padding := "2px 10px 2px 10px",
      ), elementModifier = VDomModifier(
        padding := "3px 0px 3px 0px",
      ))

    sealed trait AddProperty
    object AddProperty {
      case object None extends AddProperty
      case object CustomField extends AddProperty
      final case class DefinedField(title: String, key: String, tpe: NodeData.Type) extends AddProperty
      final case class EdgeReference(title: String, create: (NodeId, NodeId) => Edge) extends AddProperty
    }

    val addFieldMode = Var[AddProperty](AddProperty.None)

    val selfOrParentIsAutomationTemplate = Rx {
      val graph = GlobalState.rawGraph()
      graph.idToIdxFold(focusPref.nodeId)(false) { nodeIdx =>
        graph.selfOrParentIsAutomationTemplate(nodeIdx)
      }
    }

    val isCreateReference = Var(false)
    val isRenameReference = Var(false)

    addFieldMode.map[VDomModifier] {
      case AddProperty.CustomField =>
        ItemProperties.managePropertiesInline(
          ItemProperties.Target.Node(focusPref.nodeId)
        ).map(_ => AddProperty.None) --> addFieldMode
      case AddProperty.EdgeReference(title, create) =>
        ItemProperties.managePropertiesInline(
          ItemProperties.Target.Node(focusPref.nodeId),
          ItemProperties.TypeConfig(
            prefilledType = Some(NodeTypeSelection.Ref),
            hidePrefilledType = true,
            filterRefCompletion = { node =>
              val graph = GlobalState.rawGraph.now
              node.id != focusPref.nodeId && graph.idToIdxFold(node.id)(false)(graph.selfOrParentIsAutomationTemplate(_))
            },
            customOptions = Some(VDomModifier(
              UI.checkbox("Create a new node from the reference", isCreateReference),
              UI.checkbox("Rename existing node (original content in `${woost.original}`)", isRenameReference): @silent("possible missing interpolator")
            ))
          ),
          ItemProperties.EdgeFactory.Plain(create),
          ItemProperties.Names(addButton = title)
        ).map(_ => AddProperty.None) --> addFieldMode
      case AddProperty.DefinedField(title, key, tpe) =>
        ItemProperties.managePropertiesInline(
          ItemProperties.Target.Node(focusPref.nodeId),
          ItemProperties.TypeConfig(prefilledType = Some(NodeTypeSelection.Data(tpe)), hidePrefilledType = true),
          ItemProperties.EdgeFactory.labeledProperty(key),
          names = ItemProperties.Names(addButton = title)
        ).map(_ => AddProperty.None) --> addFieldMode
      case AddProperty.None => VDomModifier(
        div(
          marginLeft := "20px",
          color.gray,
          fontSize.xSmall,
          cls := "enable-text-selection",
          s"Id: ${focusPref.nodeId.toBase58}"
        ),
        div(
          cls := "ui form",
          marginTop := "10px",
          Rx {
            propertySingle().map { propertySingle =>

              VDomModifier(
                propertySingle.properties.map { property =>
                  Components.removablePropertySection(property.key, property.values, parentIdAction)
                },

                VDomModifier.ifTrue(propertySingle.info.reverseProperties.nonEmpty)(div(
                  Styles.flex,
                  flexWrap.wrap,
                  fontSize.small,
                  span("Backlinks: ", color.gray),
                  propertySingle.info.reverseProperties.map { node =>
                    Components.nodeCard(node, maxLength = Some(50)).apply(
                      margin := "3px",
                      Components.sidebarNodeFocusClickMod(Var(Some(focusPref)), pref => parentIdAction(pref.map(_.nodeId)), node.id)
                    )
                  }
                ))
              )
            }
          }
        ),
        div(
          div(
            Styles.flex,
            justifyContent.center,

            button(
              cls := "ui compact basic primary button mini",
              "+ Add Due Date",
              cursor.pointer,
              onClick.stopPropagation.use(AddProperty.DefinedField("Add Due Date", EdgeData.LabeledProperty.dueDate.key, NodeData.DateTime.tpe)) --> addFieldMode
            ),

            selfOrParentIsAutomationTemplate.map {
              case false => VDomModifier.empty
              case true => button(
                cls := "ui compact basic primary button mini",
                "+ Add Relative Due Date",
                cursor.pointer,
                onClick.stopPropagation.use(AddProperty.DefinedField("Add Relative Due Date", EdgeData.LabeledProperty.dueDate.key, NodeData.RelativeDate.tpe)) --> addFieldMode
              )
            },

            button(
              cls := "ui compact basic button mini",
              s"+ $addCustomFieldText",
              cursor.pointer,
              onClick.stopPropagation.use(AddProperty.CustomField) --> addFieldMode
            )
          ),

          selfOrParentIsAutomationTemplate.map {
            case false => VDomModifier.empty
            case true =>
              val referenceEdges = Rx {
                val graph = GlobalState.rawGraph()
                graph.idToIdxFold[flatland.ArraySliceInt](focusPref.nodeId)(flatland.ArraySliceInt.empty) { nodeIdx =>
                  graph.referencesTemplateEdgeIdx(nodeIdx)
                }
              }

              def addButton = VDomModifier(
                cursor.pointer,
                onClick.stopPropagation.useLazy(AddProperty.EdgeReference("Add Reference Template", (sourceId, targetId) => Edge.ReferencesTemplate(sourceId, EdgeData.ReferencesTemplate(isCreate = isCreateReference.now, isRename = isRenameReference.now), TemplateId(targetId)))) --> addFieldMode
              )
              def deleteButton(referenceNodeId: NodeId) = VDomModifier(
                cursor.pointer,
                //TODO: just delete correct edge...
                onClick.stopPropagation.useLazy(GraphChanges(delEdges = Array(Edge.ReferencesTemplate(focusPref.nodeId, EdgeData.ReferencesTemplate(isCreate = false), TemplateId(referenceNodeId)), Edge.ReferencesTemplate(focusPref.nodeId, EdgeData.ReferencesTemplate(isCreate = true), TemplateId(referenceNodeId))))) --> GlobalState.eventProcessor.changes
              )

              div(
                padding := "5px",
                alignItems.flexStart,
                Styles.flex,
                justifyContent.spaceBetween,

                b("Template Reference:", UI.popup := "Reference another template, such that the current node becomes the automation template for any existing node derived from the referenced template node."),

                Rx {
                  val graph = GlobalState.rawGraph()
                  div(
                    referenceEdges().map { edgeIdx =>
                      val edge = graph.edges(edgeIdx).as[Edge.ReferencesTemplate]
                      val node = graph.nodes(graph.edgesIdx.b(edgeIdx))
                      val referenceModifiers = edge.data.modifierStrings
                      val referenceModifierString = if (referenceModifiers.isEmpty) "" else referenceModifiers.mkString(",") + ": "
                      div(
                        Styles.flex,
                        alignItems.center,
                        justifyContent.flexEnd,
                        VDomModifier.ifTrue(referenceModifiers.nonEmpty)(i(marginRight := "4px", s"${referenceModifiers.mkString(", ")}: ")),
                        Components.nodeCard(node, maxLength = Some(100)).apply(
                          Components.sidebarNodeFocusClickMod(Var(Some(focusPref)), pref => parentIdAction(pref.map(_.nodeId)), node.id)
                        ),
                        div(padding := "2px", Icons.delete, deleteButton(node.id))
                      )
                    },
                    button(cls := "ui compact basic button mini", "Add Template Reference", addButton)
                  )
                }
              )
          }
        ),
        renderSplit(
          left = VDomModifier(
            searchInput("Add Tag", filter = _.role == NodeRole.Tag, createNew = createNewTag(_), showNotFound = false).foreach { tagId =>
              GlobalState.submitChanges(GraphChanges.connect(Edge.Child)(ParentId(tagId), ChildId(focusPref.nodeId)))
            }
          ),
          right = VDomModifier(
            Styles.flex,
            alignItems.center,
            flexWrap.wrap,
            Rx {
              propertySingle().map(_.info.tags.map { tag =>
                Components.removableNodeTag(tag, taggedNodeId = focusPref.nodeId)
              })
            }
          ),
        ).apply(marginTop := "10px"),
        renderSplit(
          left = VDomModifier(
            searchInput("Assign User", filter = _.data.isInstanceOf[NodeData.User]).foreach { userId =>
              GlobalState.submitChanges(GraphChanges.connect(Edge.Assigned)(focusPref.nodeId, UserId(userId)))
            }
          ),
          right = VDomModifier(
            Styles.flex,
            alignItems.center,
            flexWrap.wrap,
            Rx {
              propertySingle().map(_.info.assignedUsers.map { user =>
                Components.removableAssignedUser(user, focusPref.nodeId)
              })
            }
          )
        ).apply(marginTop := "10px"),
      )
    }
  }
}

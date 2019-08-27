package wust.webApp.views

import wust.webApp.state._
import wust.util.collection._
import scala.scalajs.js
import wust.facades.dateFns.DateFns
import flatland._
import fontAwesome.{ IconDefinition, freeRegular, freeSolid }
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.Elements.onClickDefault
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.Colors
import wust.util.macros.InlineList
import wust.webApp.Icons
import wust.webApp.state.{ FocusState, GlobalState }
import wust.webApp.views.Components._
import SharedViewElements._
import wust.webUtil.UI

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js.Date

// Unread view, this view is for showing all new unread items in the current page.
// It shows the node roles: Message, Task, Notes, Project
object NotificationView {

  val readColor = "gray"

  sealed trait Revision {
    def timestamp: EpochMilli
  }
  object Revision {
    final case class Delete(timestamp: EpochMilli) extends Revision
    final case class Edit(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
    final case class Create(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
  }
  final case class UnreadNode(nodeIdx: Int, newRevisions: List[Revision], children: js.Array[UnreadNode] = js.Array[UnreadNode]())

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val renderTime = EpochMilli.now
    val collapsed = Var(Set.empty[NodeId])

    div(
      keyed,
      Styles.growFull,
      overflow.auto,

      Styles.flex,
      justifyContent.center,

      div(
        cls := "notifications-view",
        if (BrowserDetect.isMobile) padding := "8px" else padding := "20px",

        Rx {
          val graph = GlobalState.graph()
          val userId = GlobalState.userId()
          val page = GlobalState.page()

          val unreadTree: Option[UnreadNode] = for {
            pageParentId <- page.parentId
            pageParentIdx <- graph.idToIdx(pageParentId)
            tree <- calculateUnreadTree(graph, pageParentIdx, userId, renderTime)
          } yield tree

          val currentTime = EpochMilli.now

          VDomModifier(
            unreadTree match {
              case Some(unreadTreeNode) =>
                VDomModifier(
                  div(
                    Styles.flex,
                    h3("What's new?"),
                    markAllAsReadButton("Mark everything as read", focusState.focusedId, graph, userId, renderTime)
                  ),
                  renderUnreadGroup(graph, userId, unreadTreeNode, focusedId = focusState.focusedId, renderTime = renderTime, currentTime = currentTime, collapsed, isToplevel = true)
                )
              case _ =>
                h3(
                  textAlign.center,
                  color.gray,
                  "Nothing New.",
                  padding := "10px"
                )
            }
          )
        },
        div(height := "20px") // padding bottom workaround in flexbox
      )
    )
  }

  @inline def sortUnreadNodes[T](nodes: js.Array[T], index: T => Int, graph: Graph): Unit = {
    @inline def compareDeepModified(a: Int, b: Int) = {
      val modifiedA = graph.nodeDeepModified(a)
      val modifiedB = graph.nodeDeepModified(b)
      val result = modifiedB.compare(modifiedA)
      if (result == 0) graph.nodeIds(a) compare graph.nodeIds(b) // deterministic tie break
      else result
    }

    nodes.sort { (aRaw, bRaw) =>
      val a = index(aRaw)
      val b = index(bRaw)
      val aRole = graph.nodes(a).role
      val bRole = graph.nodes(b).role
      val aIsProject = aRole == NodeRole.Project
      val bIsProject = bRole == NodeRole.Project
      if (aIsProject && bIsProject) compareDeepModified(a, b)
      else if (aIsProject && !bIsProject) 1 // put projects last
      else if (!aIsProject && bIsProject) -1 // put projects last
      else {
        // reverse order for non-projects
        compareDeepModified(b, a)
      }
    }
  }

  private def calculateUnreadTree(graph: Graph, nodeIdx: Int, userId: UserId, renderTime: EpochMilli): Option[UnreadNode] = {
    val visited = ArraySet.create(graph.nodes.length)

    def recurse(nodeIdx: Int): Option[UnreadNode] = {
      if ((visited contains nodeIdx) || !UnreadComponents.nodeRoleIsAccepted(graph.nodes(nodeIdx).role)) None
      else {
        visited += nodeIdx

        if (graph.hasChildrenIdx(nodeIdx)) {
          val unreadChildren = js.Array[UnreadNode]()
          graph.childrenIdx.foreachElement(nodeIdx){ childIdx =>
            recurse(childIdx) match {
              case Some(unreadTreeNode) => unreadChildren += unreadTreeNode
              case _                    =>
            }
          }
          sortUnreadNodes[UnreadNode](unreadChildren, index = _.nodeIdx, graph)
          constructUnreadTreeNode(nodeIdx, graph, userId, renderTime, unreadChildren)
        } else {
          val isUnread = UnreadComponents.nodeIsUnread(graph, userId, nodeIdx)
          constructUnreadTreeNode(nodeIdx, graph, userId, renderTime, js.Array[UnreadNode]())
        }
      }
    }

    recurse(nodeIdx)
  }

  def constructUnreadTreeNode(nodeIdx: Int, graph: Graph, userId: UserId, renderTime: EpochMilli, children: js.Array[UnreadNode]): Option[UnreadNode] = {
    def appendAuthorship(nodeIdx: Int, lastAuthorship: UnreadComponents.Authorship, seen: Boolean): UnreadNode = {
      import lastAuthorship._
      val revision =
        if (isCreation) Revision.Create(author, timestamp, seen = seen)
        else Revision.Edit(author, timestamp, seen = seen)

      UnreadNode(nodeIdx, revision :: Nil, children = children)
    }

    UnreadComponents.readStatusOfNode(graph, userId, nodeIdx) match {
      case UnreadComponents.ReadStatus.SeenAt(timestamp, lastAuthorship) if timestamp > renderTime =>
        Some(appendAuthorship(nodeIdx, lastAuthorship, seen = true))
      case UnreadComponents.ReadStatus.Unseen(lastAuthorship) =>
        Some(appendAuthorship(nodeIdx, lastAuthorship, seen = false))
      case _ =>
        if (children.nonEmpty) Some(UnreadNode(nodeIdx, Nil, children))
        else None
    }
  }

  private def renderUnreadGroup(
    graph: Graph,
    userId: UserId,
    unreadParentNodeInitial: UnreadNode,
    focusedId: NodeId,
    renderTime: EpochMilli,
    currentTime: EpochMilli,
    collapsed: Var[Set[NodeId]],
    isToplevel: Boolean = false
  )(implicit ctx: Ctx.Owner): VDomModifier = {

    var unreadParentNode = unreadParentNodeInitial
    if (!isToplevel) {
      // skip chains of already read nodes
      while (unreadParentNode.children.size == 1 && unreadParentNode.children.head.newRevisions.isEmpty)
        unreadParentNode = unreadParentNode.children.head
    }

    val parentId = graph.nodeIds(unreadParentNode.nodeIdx)

    def breadCrumbs = BreadCrumbs(
      graph,
      start = BreadCrumbs.EndPoint.Node(focusedId),
      end = BreadCrumbs.EndPoint.Node(parentId),
      clickAction = nodeId => GlobalState.rightSidebarNode.update({
        case Some(pref) if pref.nodeId == nodeId => None
        case _                                   => Some(FocusPreference(nodeId))
      }: Option[FocusPreference] => Option[FocusPreference])
    )

    val expandToggleButton = Rx {
      val toggleIcon =
        if (!collapsed().contains(parentId)) freeSolid.faAngleDown: VDomModifier
        else freeSolid.faAngleRight: VDomModifier

      VDomModifier.ifTrue(hasDeepUnreadChildren(graph, parentId, userId, renderTime))(
        div(
          cls := "expand-collapsebutton",
          div(toggleIcon, cls := "fa-fw"),
          onClickDefault.foreach{ _ => collapsed.update(_.toggle(parentId)) }
        )
      )
    }

    VDomModifier(
      VDomModifier.ifNot(isToplevel)(
        div(
          cls := "notifications-header",

          expandToggleButton,
          markAllAsReadIcon(parentId, graph, userId, renderTime),
          breadCrumbs.append(marginRight.auto),
        )
      ),
      Rx {
        val selfCollapsed = collapsed().contains(parentId)
        VDomModifier.ifTrue(!selfCollapsed)(
          div(
            cls := "notifications-body",

            div(
              unreadParentNode.children.map { unreadNode =>
                graph.nodes(unreadNode.nodeIdx) match {
                  case node: Node.Content => // node is always Content
                    val (revisionTable, allSeen, deletedTime) = renderRevisions(graph, unreadNode, node, focusedId, currentTime)

                    VDomModifier(
                      VDomModifier.ifTrue(unreadNode.newRevisions.nonEmpty)(
                        div(
                          cls := "unread-row",
                          div(
                            cls := "unread-row-dot",
                            if (!allSeen) VDomModifier(
                              freeSolid.faCircle,
                              color := Colors.unread,
                            )
                            else VDomModifier(
                              freeRegular.faCircle,
                              color := readColor,
                            ),

                            onClick.stopPropagation.foreach {
                              val changes = if (allSeen) GraphChanges.from(delEdges = GlobalState.graph.now.readEdgeIdx.flatMap[Edge.Read](GlobalState.graph.now.idToIdxOrThrow(node.id)) { idx =>
                                val edge = GlobalState.graph.now.edges(idx).as[Edge.Read]
                                if (edge.userId == GlobalState.user.now.id && edge.data.timestamp >= renderTime) Array(edge) else Array.empty
                              })
                              else GraphChanges(
                                addEdges = Array(Edge.Read(node.id, EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))
                              )

                              GlobalState.submitChanges(changes)
                              ()
                            }
                          ),
                          VDomModifier.ifTrue(allSeen)(opacity := 0.5),
                          node.role match {
                            case NodeRole.Message => div(cls := "prefix-icon fa-fw", Icons.message)
                            case NodeRole.Task    => div(cls := "prefix-icon fa-fw", Icons.task)
                            case NodeRole.Note    => div(cls := "prefix-icon fa-fw", Icons.note)
                            case _                => VDomModifier.empty
                          },
                          nodeCard(node, maxLength = Some(150), projectWithIcon = true).apply(
                            VDomModifier.ifTrue(deletedTime.isDefined)(cls := "node-deleted"),
                            Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, node.id)
                          ),

                          revisionTable(paddingTop := "5px")
                        )
                      ),
                      VDomModifier.ifTrue(unreadNode.children.nonEmpty){
                        renderUnreadGroup(graph, userId, unreadNode, focusedId = graph.nodeIds(unreadNode.nodeIdx), renderTime = renderTime, currentTime = currentTime, collapsed = collapsed)
                      }
                    )

                  case _ => VDomModifier.empty
                }
              }
            )
          )
        )
      }
    )
  }

  private def renderRevisions(
    graph: Graph,
    unreadNode: UnreadNode,
    node: Node.Content,
    focusedId: NodeId,
    currentTime: EpochMilli
  ): (VNode, Boolean, Option[EpochMilli]) = {
    val deletedTime = graph.parentEdgeIdx(unreadNode.nodeIdx).find { idx =>
      val edge = graph.edges(idx).as[Edge.Child]
      edge.parentId == focusedId
    }.flatMap { idx =>
      val edge = graph.edges(idx).as[Edge.Child]
      edge.data.deletedAt
    }

    val newRevisionsWithDelete = deletedTime match {
      case Some(time) => unreadNode.newRevisions :+ Revision.Delete(time)
      case _          => unreadNode.newRevisions
    }

    var allSeen = true

    def revisionVisuals(revision: Revision): (IconDefinition, String, Option[Node.User], Boolean) = {
      revision match {
        case revision: Revision.Edit =>
          allSeen = allSeen && revision.seen
          (freeSolid.faEdit, s"edited", Some(revision.author), revision.seen)
        case revision: Revision.Create =>
          allSeen = allSeen && revision.seen
          (freeSolid.faPlus, "", Some(revision.author), revision.seen)
        case revision: Revision.Delete => (freeSolid.faTrash, s"archived", None, true)
      }
    }

    def authorModifiers(doAuthor: Option[Node.User]) = VDomModifier(
      doAuthor.map { author =>
        div(
          fontWeight.bold,
          Styles.flex,
          alignItems.center,
          Components.nodeAvatar(author, size = 12).apply(Styles.flexStatic, marginRight := "3px"),
          Components.displayUserName(author.data),
        )
      }
    )

    def timestampModifiers(timestamp: EpochMilli) = s"${DateFns.formatDistance(new Date(timestamp), new Date(currentTime))} ago"

    val tableNode = div(
      newRevisionsWithDelete.map { revision =>
        val (doIcon, doDescription, doAuthor, isSeen) = revisionVisuals(revision)

        div(
          Styles.flex,
          justifyContent.flexStart,
          flexWrap.wrap,

          VDomModifier.ifTrue(isSeen)(opacity := 0.5),
          fontSize := "0.8em",

          div(authorModifiers(doAuthor), marginLeft := "1em"),
          div(s"$doDescription ${timestampModifiers(revision.timestamp)}", marginLeft := "1em"),
        )
      }
    )

    (tableNode, allSeen, deletedTime)
  }

  def markAllAsReadButton(text: String, parentId: NodeId, graph: Graph, userId: UserId, renderTime: EpochMilli) = {
    button(
      cls := "ui tiny compact button",
      text,
      marginLeft := "auto",
      marginRight := "0px", // remove semantic ui button margin
      marginTop := "3px",
      marginBottom := "3px",
      Styles.flexStatic,

      onClick.stopPropagation.foreach {
        val changes = GraphChanges(
          addEdges = calculateDeepUnreadChildren(graph, parentId, userId, renderTime = renderTime)
            .map(nodeIdx => Edge.Read(GlobalState.graph.now.nodeIds(nodeIdx), EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))(breakOut)
        )

        GlobalState.submitChanges(changes)
        ()
      }
    )
  }

  def markAllAsReadIcon(parentId: NodeId, graph: Graph, userId: UserId, renderTime: EpochMilli) = {
    div(
      UI.tooltip("top center") := "Mark all as read",
      Styles.flexStatic,
      cls := "fa-fw",

      if (hasDeepUnreadChildren(graph, parentId, userId, EpochMilli.now)) VDomModifier(
        freeSolid.faArrowAltCircleDown,
        color := Colors.unread,
        onClickDefault.foreach {
          val changes = GraphChanges(
            addEdges = calculateDeepUnreadChildren(graph, parentId, userId, renderTime = renderTime)
              .map(nodeIdx => Edge.Read(GlobalState.graph.now.nodeIds(nodeIdx), EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))(breakOut)
          )

          GlobalState.submitChanges(changes)
          ()
        }
      )
      else VDomModifier(
        freeRegular.faArrowAltCircleDown,
        color := readColor,
      )
    )
  }

  private def calculateDeepUnreadChildren(graph: Graph, parentNodeId: NodeId, userId: UserId, renderTime: EpochMilli): js.Array[Int] = {
    val unreadNodes = js.Array[Int]()

    graph.idToIdx(parentNodeId).foreach { parentNodeIdx =>
      graph.descendantsIdxForeach(parentNodeIdx) { nodeIdx =>
        UnreadComponents.readStatusOfNode(graph, userId, nodeIdx) match {
          case UnreadComponents.ReadStatus.SeenAt(timestamp, lastAuthorship) if timestamp > renderTime =>
            unreadNodes += nodeIdx
          case UnreadComponents.ReadStatus.Unseen(lastAuthorship) =>
            unreadNodes += nodeIdx
          case _ => ()
        }
      }
    }

    unreadNodes
  }

  private def hasDeepUnreadChildren(graph: Graph, parentNodeId: NodeId, userId: UserId, renderTime: EpochMilli): Boolean = {
    graph.idToIdx(parentNodeId).fold(false) { parentNodeIdx =>
      graph.descendantsIdxExists(parentNodeIdx) { nodeIdx =>
        UnreadComponents.readStatusOfNode(graph, userId, nodeIdx) match {
          case UnreadComponents.ReadStatus.SeenAt(timestamp, lastAuthorship) if timestamp > renderTime => true
          case UnreadComponents.ReadStatus.Unseen(lastAuthorship) => true
          case _ => false
        }
      }
    }
  }

}

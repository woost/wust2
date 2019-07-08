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

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js.Date

// Unread view, this view is for showing all new unread items in the current page.
// It shows the node roles: Message, Task, Notes, Project
object NotificationView {

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
    val expanded = Var(Set(focusState.focusedId))

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      cls := "notifications-view",

      if (BrowserDetect.isMobile) padding := "8px" else padding := "20px",

      Rx {
        val graph = GlobalState.graph()
        val userId = GlobalState.user().id
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
                  markAllAsReadButton( "Mark everything as read", focusState.focusedId, graph, userId, renderTime)
                ),
                renderUnreadGroup( graph, userId, unreadTreeNode, focusedId = focusState.focusedId, renderTime = renderTime, currentTime = currentTime, expanded, isToplevel = true)
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
    expanded: Var[Set[NodeId]],
    isToplevel: Boolean = false
  )(implicit ctx: Ctx.Owner): VDomModifier = {

    var unreadParentNode = unreadParentNodeInitial
    if (!isToplevel) {
      // skip chains of already read nodes
      while (unreadParentNode.children.size == 1 && unreadParentNode.children.head.newRevisions.isEmpty)
        unreadParentNode = unreadParentNode.children.head
    }

    val parentId = graph.nodeIds(unreadParentNode.nodeIdx)
    val breadCrumbs = Rx {
      BreadCrumbs(
        
        graph,
        GlobalState.user(),
        Some(focusedId),
        parentId = Some(parentId),
        parentIdAction = nodeId => GlobalState.rightSidebarNode.update({
          case Some(pref) if pref.nodeId == nodeId => None
          case _                                   => Some(FocusPreference(nodeId))
        }: Option[FocusPreference] => Option[FocusPreference])
      )
    }

    val deepUnreadChildrenCount = {
      calculateDeepUnreadChildren(graph, parentId, userId, renderTime).length
    }
    val expandToggleButton = Rx {
      val toggleIcon =
        if (expanded().contains(parentId)) freeSolid.faAngleDown: VDomModifier
        else freeSolid.faAngleRight: VDomModifier

      VDomModifier.ifTrue(deepUnreadChildrenCount > 0)(
        div(
          cls := "expand-collapsebutton",
          cursor.pointer,
          div(toggleIcon, cls := "fa-fw"),
          onClick.stopPropagation.foreach{ _ => expanded.update(_.toggle(parentId)) }
        )
      )
    }

    val deepUnreadChildrenLabel = Rx {
      VDomModifier.ifTrue(deepUnreadChildrenCount > 0)(
        UnreadComponents.unreadLabelElement(
          deepUnreadChildrenCount,
        // marginRight := "0px"
        )
      )
    }

    VDomModifier(
      VDomModifier.ifNot(isToplevel)(
        div(
          cls := "notifications-header",

          expandToggleButton,
          breadCrumbs,
          deepUnreadChildrenLabel,
          markAllAsReadButton( "Mark all as read", parentId, graph, userId, renderTime)
        )
      ),
      Rx {
        val selfExpanded = expanded().contains(parentId)
        VDomModifier.ifTrue(selfExpanded)(
          div(
            cls := "ui segment",
            marginTop := "0px", // remove semantic ui marginTop
            table(
              unreadParentNode.children.map { unreadNode =>
                graph.nodes(unreadNode.nodeIdx) match {
                  case node: Node.Content => // node is always Content
                    val (revisionTable, allSeen, deletedTime) = renderRevisions(graph, unreadNode, node, focusedId, currentTime)

                    VDomModifier(
                      VDomModifier.ifTrue(unreadNode.newRevisions.nonEmpty)(
                        tr(
                          td(
                            width := "400px",
                            VDomModifier.ifTrue(allSeen)(opacity := 0.5),
                            nodeCard( node, maxLength = Some(150), projectWithIcon = true).apply(
                              VDomModifier.ifTrue(deletedTime.isDefined)(cls := "node-deleted"),
                              Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, node.id)
                            )
                          ),

                          td(
                            revisionTable
                          ),

                          td(
                            width := "20px",
                            cursor.pointer,

                            //TODO: hack for having a better layout on mobile with this table
                            if (BrowserDetect.isMobile)
                              marginTop := "-6px"
                            else
                              padding := "5px",

                            textAlign.right,
                            if (allSeen) VDomModifier(
                              freeRegular.faCircle,
                              color.gray
                            )
                            else VDomModifier(
                              color := Colors.unread,
                              freeSolid.faCircle
                            ),

                            onClick.stopPropagation.foreach {
                              val changes = if (allSeen) GraphChanges.from(delEdges = GlobalState.graph.now.readEdgeIdx.flatMap[Edge.Read](GlobalState.graph.now.idToIdxOrThrow(node.id)) { idx =>
                                val edge = GlobalState.graph.now.edges(idx).as[Edge.Read]
                                if (edge.userId == GlobalState.user.now.id && edge.data.timestamp >= renderTime) Array(edge) else Array.empty
                              })
                              else GraphChanges(
                                addEdges = Array(Edge.Read(node.id, EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))
                              )

                              GlobalState.eventProcessor.changes.onNext(changes)
                              ()
                            }
                          )
                        )
                      ),
                      VDomModifier.ifTrue(unreadNode.children.nonEmpty){
                        tr(
                          td(
                            colSpan := 3,
                            paddingRight := "0px",
                            paddingLeft := "0px",
                            renderUnreadGroup( graph, userId, unreadNode, focusedId = graph.nodeIds(unreadNode.nodeIdx), renderTime = renderTime, currentTime = currentTime, expanded = expanded)
                          )
                        )
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
          (freeSolid.faEdit, s"Edited ${node.role}", Some(revision.author), revision.seen)
        case revision: Revision.Create =>
          allSeen = allSeen && revision.seen
          (freeSolid.faPlus, s"Created ${node.role}", Some(revision.author), revision.seen)
        case revision: Revision.Delete => (freeSolid.faTrash, s"Archived ${node.role}", None, true)
      }
    }

    def descriptionModifiers(doIcon: IconDefinition, doDescription: String) = VDomModifier(
      color.gray,
      span(
        display.inlineBlock,
        cls := "fa-fw",
        doIcon,
        marginRight := "5px"
      ),
      doDescription
    )

    def authorModifiers(doAuthor: Option[Node.User]) = VDomModifier(
      doAuthor.map { author =>
        div(
          fontSize := "0.8em",
          fontWeight.bold,
          Styles.flex,
          alignItems.center,
          Components.nodeAvatar(author, size = 12).apply(Styles.flexStatic, marginRight := "3px"),
          Components.displayUserName(author.data),
          marginLeft.auto
        )
      }
    )

    def timestampModifiers(timestamp: EpochMilli) = VDomModifier(
      fontSize.smaller,
      s"${DateFns.formatDistance(new Date(timestamp), new Date(currentTime))} ago"
    )

    val tableNode = if (BrowserDetect.isMobile) div(
      newRevisionsWithDelete.map { revision =>
        val (doIcon, doDescription, doAuthor, isSeen) = revisionVisuals(revision)

        div(
          Styles.flex,
          justifyContent.spaceBetween,
          flexWrap.wrap,

          VDomModifier.ifTrue(isSeen)(opacity := 0.5),

          div(authorModifiers(doAuthor)),
          div(descriptionModifiers(doIcon, doDescription)),
          div(timestampModifiers(revision.timestamp))
        )
      }
    )
    else table(
      cls := "ui compact fixed table",
      cls := "no-inner-table-borders",
      border := "none",
      newRevisionsWithDelete.map { revision =>
        val (doIcon, doDescription, doAuthor, isSeen) = revisionVisuals(revision)

        tr(
          VDomModifier.ifTrue(isSeen)(opacity := 0.5),

          td(authorModifiers(doAuthor)),
          td(descriptionModifiers(doIcon, doDescription)),
          td(timestampModifiers(revision.timestamp))
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

      onClick.stopPropagation.foreach {
        val changes = GraphChanges(
          addEdges = calculateDeepUnreadChildren(graph, parentId, userId, renderTime = renderTime)
            .map(nodeIdx => Edge.Read(GlobalState.graph.now.nodeIds(nodeIdx), EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))(breakOut)
        )

        GlobalState.eventProcessor.changes.onNext(changes)
        ()
      }
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

}

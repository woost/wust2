package wust.webApp.views

import acyclic.file
import wust.webUtil.Elements.onClickDefault
import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import rx._
import wust.graph._
import wust.ids._
import wust.sdk.Colors
import wust.util.macros.InlineList
import wust.webApp._
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.state.GlobalState
import wust.webUtil.UI
import wust.webUtil.outwatchHelpers._

// This file contains woost-related UI helpers.

object UnreadComponents {

  val unreadLabelElement = div(
    cls := "ui label unread-label",
  )

  val unreadDot = span(
    cls := "unread-dot",
    freeSolid.faCircle,
  )

  sealed trait ReadStatus
  object ReadStatus {
    case class SeenAt(timestamp: EpochMilli, lastAuthorship: Authorship) extends ReadStatus
    case class Unseen(lastAuthorship: Authorship) extends ReadStatus
    case object Ignore extends ReadStatus
  }
  case class Authorship(author: Node.User, timestamp: EpochMilli, isCreation: Boolean)

  case class Activity(authorship: Authorship, isSeen: Boolean)

  private def findLastReadTime(graph: Graph, userId: UserId, nodeIdx: Int): Option[EpochMilli] = {
    var lastReadTime: Option[EpochMilli] = None
    graph.readEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
      val edge = graph.edges(edgeIdx).as[Edge.Read]
      if (edge.userId == userId && lastReadTime.forall(_ < edge.data.timestamp)) {
        lastReadTime = Some(edge.data.timestamp)
      }
    }
    lastReadTime
  }
  private def findLastAuthorshipFromSomeoneElse(graph: Graph, userId: UserId, nodeIdx: Int): Option[Authorship] = {
    val sliceLength = graph.sortedAuthorshipEdgeIdx.sliceLength(nodeIdx)
    if (sliceLength > 0) {
      val edgeIdx = graph.sortedAuthorshipEdgeIdx(nodeIdx, sliceLength - 1)
      val edge = graph.edges(edgeIdx).as[Edge.Author]
      val author = graph.nodes(graph.edgesIdx.b(edgeIdx)).as[Node.User]
      if (userId == author.id) None
      else Some(Authorship(author, edge.data.timestamp, isCreation = sliceLength == 1))
    } else None
  }

  // check is node is not read yet (if it can be read)
  def nodeIsUnread(graph:Graph, userId:UserId, nodeIdx:Int):Boolean = {
    @inline def nodeWasModifiedAfterUserRead = {
      findLastAuthorshipFromSomeoneElse(graph, userId, nodeIdx).fold(false) { lastAuthorship =>
        !graph.readEdgeIdx.exists(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).as[Edge.Read]
          edge.targetId == userId && (edge.data.timestamp isAfterOrEqual lastAuthorship.timestamp)
        }
      }
    }

    nodeIsReadable(graph, userId, nodeIdx) && nodeWasModifiedAfterUserRead
  }
  // dual of nodeIsUnread, but returns more information about the readstatus
  // whether it is ignored in read calculation, whether the change was seen and
  // when, or whether an authorship was never seen.
  def readStatusOfNode(graph:Graph, userId:UserId, nodeIdx:Int):ReadStatus = {
    @inline def seenTimestamp(lastAuthorship: Authorship): ReadStatus = {
      val seenLastAuthorshipAt = findLastReadTime(graph, userId, nodeIdx).filter(_ isAfterOrEqual lastAuthorship.timestamp)
      seenLastAuthorshipAt.fold[ReadStatus](ReadStatus.Unseen(lastAuthorship))(ReadStatus.SeenAt(_, lastAuthorship))
    }

    if (nodeIsActivity(graph, nodeIdx))
      findLastAuthorshipFromSomeoneElse(graph, userId, nodeIdx).fold[ReadStatus](ReadStatus.Ignore)(seenTimestamp)
    else
      ReadStatus.Ignore
  }

  // returns lastreadtime
  def activitiesOfNode(graph:Graph, userId:UserId, nodeIdx:Int, showAllRevisions: Boolean)(collect: Activity => Unit): Option[EpochMilli] = {
    val lastReadTime = findLastReadTime(graph, userId, nodeIdx)
    var isFirst = true
    var lastAuthorship: Authorship = null
    var lastActivity: Activity = null
    graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
      val edge = graph.edges(edgeIdx).as[Edge.Author]
      val author = graph.nodes(graph.edgesIdx.b(edgeIdx)).as[Node.User]
      val authorship = Authorship(author, edge.data.timestamp, isCreation = isFirst)

      // TODO: filter out duplicate author edges with very similar time that we have from
      // a multiple edit bug.  delete from db? currently jitter is 1 seconds.
      if (lastAuthorship == null || (authorship.author.id != lastAuthorship.author.id || authorship.timestamp.isAfterOrEqual(EpochMilli(lastAuthorship.timestamp + 1000L)))) {
        val isSeen = lastReadTime.exists(_ isAfterOrEqual authorship.timestamp)
        val activity = Activity(authorship, isSeen = isSeen)
        if (showAllRevisions) collect(activity)
        else lastActivity = activity
      }

      isFirst = false
      lastAuthorship = authorship
    }

    if (lastActivity != null) collect(lastActivity)

    lastReadTime
  }

  @inline def nodeRoleIsAccepted(role: NodeRole) = InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Project, NodeRole.Note, NodeRole.Task)(role)

  def nodeIsActivity(graph:Graph, nodeIdx:Int):Boolean = {
    @inline def nodeHasContentRole = {
      val node = graph.nodes(nodeIdx)
      nodeRoleIsAccepted(node.role)
    }

    nodeHasContentRole
  }

  def nodeIsReadable(graph:Graph, userId:UserId, nodeIdx:Int):Boolean = {
    @inline def nodeHasContentRole = {
      val node = graph.nodes(nodeIdx)
      nodeRoleIsAccepted(node.role)
    }
    // @inline def userIsMemberOfParent = {
    //   graph.idToIdxFold(userId)(false){userIdx =>
    //   graph.parentsIdx.exists(nodeIdx)(parentIdx => graph.userIsMemberOf(userIdx, parentIdx))}
    // }
    @inline def nodeIsNotDerivedFromTemplate = !graph.isDerivedFromTemplate(nodeIdx)

    nodeHasContentRole && nodeIsNotDerivedFromTemplate
  }

  def readObserver(nodeId: NodeId, labelModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VDomModifier = {

    val nodeIdx = GlobalState.graph.map(_.idToIdxOrThrow(nodeId))

    val isUnread = Rx {
      val graph = GlobalState.graph()
      val userId = GlobalState.userId()

      nodeIsUnread(graph, userId, nodeIdx())
    }

    val unreadChildren = Rx {
      val graph = GlobalState.graph()
      val userId = GlobalState.userId()

      graph.descendantsIdxCount(nodeIdx())(idx => nodeIsUnread(graph, userId, idx))
    }


    val unreadLabel = unreadLabelElement.apply(
      alignSelf.center,
      labelModifier,
    )

    var observed = Var(false)

    Rx {
      isUnread() match {
        case true => VDomModifier(
          unreadChildren() match {
            case 0 => unreadDot(Rx{VDomModifier.ifTrue(observed())(opacity := 0.0)})
            case count => unreadLabel(count)
          },

          managedElement.asHtml { elem =>
            val observer = new IntersectionObserver(
              { (entry, observer) =>
                val isIntersecting = entry.head.isIntersecting
                if (isIntersecting && isUnread.now) {
                  val markAsReadChanges = GraphChanges(
                    addEdges = Array(Edge.Read(nodeId, EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))
                  )

                  // stop observing once read
                  observer.unobserve(elem)
                  observer.disconnect()

                  observed() = true // makes the dot fade out

                  GlobalState.eventProcessor.changesRemoteOnly.onNext(markAsReadChanges)
                }
              },
              new IntersectionObserverOptions {
                root = null //TODO need actual scroll-parent?
                // TODO: rootMargin = "100px 0px 0px 0px"
                threshold = 0
              }
            )

            observer.observe(elem)

            Subscription { () =>
              observer.unobserve(elem)
              observer.disconnect()
            }
          }
        )
        case false =>
          unreadChildren() match {
            case 0 => VDomModifier.empty
            case count => unreadLabel(count)
          }
      },
    }
  }

  def activityButtons(nodeId: NodeId, modifiers: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[View.Visible, VDomModifier] = EmitterBuilder.ofModifier { sink =>

    val haveUnreadNotifications = Rx {
      val graph = GlobalState.graph()
      val userId = GlobalState.userId()
      hasUnreadChildren(graph, nodeId, deep = true, userId)
    }

    val channelNotification = Rx {
      VDomModifier.ifTrue(haveUnreadNotifications())(
        button(
          cls := "ui mini inverted compact button",
          Icons.notifications,
          UI.tooltip := "Unread Items",
          onClickDefault.use(View.Notifications) --> sink,
        )
      )
    }

    val activityStream = button(
      cls := "ui mini inverted compact button",
      Icons.activityStream,
      UI.tooltip := "Activity Stream",
      onClickDefault.use(View.ActivityStream) --> sink,
    )

    div(
      cls := "ui mini compact buttons",
      channelNotification,
      activityStream,
      modifiers
    )
  }

  // check whether there are unread nodes for the user within parentNodeId
  private def hasUnreadChildren(graph: Graph, parentNodeId: NodeId, deep: Boolean, userId: UserId): Boolean = {
    @inline def foreachChildren(parentNodeIdx: Int)(code: Int => Unit) = {
      if (deep) graph.descendantsIdxForeach(parentNodeIdx)(code)
      else graph.childrenIdx.foreachElement(parentNodeIdx)(code)
    }

    graph.idToIdx(parentNodeId).foreach { parentNodeIdx =>
      foreachChildren(parentNodeIdx) { nodeIdx =>
        if (UnreadComponents.nodeIsUnread(graph, userId, nodeIdx))
          return true
      }
    }

    false
  }
}

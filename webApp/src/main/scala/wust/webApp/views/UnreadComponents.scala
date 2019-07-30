package wust.webApp.views

import wust.api.AuthUser
import acyclic.file
import wust.webUtil.outwatchHelpers._
import cats.effect.IO
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.{SearchOptions, SearchSourceEntry}
import wust.facades.jquery.JQuerySelection
import wust.facades.marked.Marked
import fontAwesome._
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, Ownable, UI}
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, Colors, NodeColor}
import wust.util.StringOps._
import wust.util._
import wust.util.macros.InlineList
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.state.{EmojiReplacer, FocusPreference, GlobalState}
import wust.webApp.views.UploadComponents._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

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

    if (nodeIsReadable(graph, userId, nodeIdx))
      findLastAuthorshipFromSomeoneElse(graph, userId, nodeIdx).fold[ReadStatus](ReadStatus.Ignore)(seenTimestamp)
    else
      ReadStatus.Ignore
  }

  @inline def nodeRoleIsAccepted(role: NodeRole) = InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Project, NodeRole.Note, NodeRole.Task)(role)

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
      val user = GlobalState.user()

      nodeIsUnread(graph, user.id, nodeIdx())
    }

    val unreadChildren = Rx {
      val graph = GlobalState.graph()
      val user = GlobalState.user()

      graph.descendantsIdxCount(nodeIdx())(idx => nodeIsUnread(graph, user.id, idx))
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
            case 0 => unreadDot(Rx{VDomModifier.ifTrue(observed())(color := Colors.nodecardBg)})
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

            Cancelable { () =>
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

  def notificationsButton(nodeId: NodeId, modifiers: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[View.Visible, VDomModifier] = EmitterBuilder.ofModifier { sink =>

    val haveUnreadNotifications = Rx {
      val graph = GlobalState.graph()
      val user = GlobalState.user()
      hasUnreadChildren(graph, nodeId, deep = true, user)
    }

    val channelNotification = Rx {
      VDomModifier.ifTrue(haveUnreadNotifications())(
        button(
          cls := "ui compact inverted button",
          Icons.notifications,
          onClick.stopPropagation(View.Notifications) --> sink,
          modifiers,
        )
      )
    }
    channelNotification
  }

  // check whether there are unread nodes for the user within parentNodeId
  private def hasUnreadChildren(graph: Graph, parentNodeId: NodeId, deep: Boolean, user: AuthUser): Boolean = {
    @inline def foreachChildren(parentNodeIdx: Int)(code: Int => Unit) = {
      if (deep) graph.descendantsIdxForeach(parentNodeIdx)(code)
      else graph.childrenIdx.foreachElement(parentNodeIdx)(code)
    }

    graph.idToIdx(parentNodeId).foreach { parentNodeIdx =>
      foreachChildren(parentNodeIdx) { nodeIdx =>
        if (UnreadComponents.nodeIsUnread(graph, user.id, nodeIdx))
          return true
      }
    }

    false
  }
}

package wust.webApp.views

import acyclic.file
import wust.webUtil.outwatchHelpers._
import cats.effect.IO
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.{SearchOptions, SearchSourceEntry}
import wust.facades.jquery.JQuerySelection
import wust.facades.marked.Marked
import wust.facades.woostConfig.WoostConfig
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

  val unreadStyle = VDomModifier(
    float.right,
    marginLeft := "5px",
    marginRight := "5px",
  )

  val unreadLabelElement = div(
    cls := "ui label",
    color := "white",
    fontSize.xSmall,
    unreadStyle,
    backgroundColor := Colors.unread,
  )

  val unreadDot = span(
    freeSolid.faCircle,
    color := Colors.unread,
    transition := "color 10s",
    transitionDelay := "5s",
    unreadStyle
  )

  def nodeIsUnread(graph:Graph, userId:UserId, nodeIdx:Int):Boolean = {
    @inline def nodeHasContentRole = {
      val node = graph.nodes(nodeIdx)
      InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Project, NodeRole.Note, NodeRole.Task)(node.role)
    }
    @inline def userIsMemberOfParent = {
      graph.idToIdxFold(userId)(false){userIdx =>
      graph.parentsIdx.exists(nodeIdx)(parentIdx => graph.userIsMemberOf(userIdx, parentIdx))}
    }
    @inline def nodeIsNotDerivedFromTemplate = !graph.isDerivedFromTemplate(nodeIdx)

    @inline def nodeWasModifiedAfterUserRead = {
      val lastModification = graph.nodeModified(nodeIdx)
      graph.readEdgeIdx.forall(nodeIdx) { edgeIdx =>
        val edge = graph.edges(edgeIdx).as[Edge.Read]
        edge.targetId == userId && (lastModification isAfterOrEqual edge.data.timestamp)
      }
    }

    //TODO: reject own edits/creations

    nodeHasContentRole &&
    nodeIsNotDerivedFromTemplate &&
    userIsMemberOfParent  &&
    nodeWasModifiedAfterUserRead
  }

  def readObserver(state: GlobalState, nodeId: NodeId, labelModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VDomModifier = {

    val nodeIdx = state.graph.map(_.idToIdxOrThrow(nodeId))

    val isUnread = Rx {
      val graph = state.graph()
      val user = state.user()

      nodeIsUnread(graph, user.id, nodeIdx())
    }

    val unreadChildren = Rx {
      val graph = state.graph()
      val user = state.user()

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
                    addEdges = Array(Edge.Read(nodeId, EdgeData.Read(EpochMilli.now), state.user.now.id))
                  )

                  // stop observing once read
                  observer.unobserve(elem)
                  observer.disconnect()

                  observed() = true // makes the dot fade out

                  state.eventProcessor.changesRemoteOnly.onNext(markAsReadChanges)
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
}

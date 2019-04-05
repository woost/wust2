package wust.webApp.views

import scala.scalajs.js
import monix.execution.Cancelable
import wust.webApp.dragdrop.{DragContainer, DragItem}
import fontAwesome.freeSolid
import SharedViewElements._
import wust.webApp.{BrowserDetect, Icons, ItemProperties}
import wust.webApp.Icons
import outwatch.dom._
import wust.sdk.{BaseColors, NodeColor}
import outwatch.dom.dsl._
import styles.extra.{transform, transformOrigin}
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.views.Elements._
import monix.reactive.subjects.{BehaviorSubject, PublishSubject}
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}
import wust.webApp.views.Components._
import wust.util._
import d3v4._
import org.scalajs.dom.console

// Timeline which uses properties for start and enddate
object GanttView {

  case class Bar(node:Node, yPos: Int, startDate:Option[EpochMilli], endDate:Option[EpochMilli])

  def getFirstDate(propertyMap: Map[String, Array[PropertyData.PropertyValue]], search: String):Option[EpochMilli] = {
    propertyMap
      .get(search)
      .flatMap(_.collectFirst{
      case PropertyData.PropertyValue(_, Node.Content(_, data:NodeData.Date, _, _, _)) => data.content
    })
  }

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val bars:Rx[Array[Bar]] = Rx {
      val graph = state.graph()
      val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

      var i = 0
      graph.childrenIdx.flatMap(nodeIdx) { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        if(node.role == NodeRole.Task) {
          val propertyData = PropertyData.Single(graph, nodeIdx)
          val startDate:Option[EpochMilli] = getFirstDate(propertyData.info.propertyMap, "startdate")
          val endDate:Option[EpochMilli] = getFirstDate(propertyData.info.propertyMap, "enddate")
          val yPos = i
          i += 1
          Array(Bar(node, yPos, startDate, endDate))
        } else Array.empty[Bar]
      }
    }

    val unit:Long = EpochMilli.day
    val unitWidth:Double = 20
    val now = EpochMilli.now
    var currentTransform: Var[Transform] = Var(d3.zoomIdentity)

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",

      Styles.flex,
      flexDirection.column,

      SharedViewElements.inputRow(
        state,
        submitAction = { str =>
          val newNode = Node.MarkdownTask(str)
          val changes = GraphChanges.addNodesWithParents(newNode :: Nil, state.page.now.parentId.map(ParentId(_)))
          state.eventProcessor.changes.onNext(changes)
        },
        placeHolderMessage = Some("Add a task"),
        showMarkdownHelp = true
      ),
    div(
      backgroundColor := "rgba(255,255,255,0.5)",
      height := "100%",
      overflow.hidden,
      onD3Zoom(currentTransform() = _),
      position.relative,
      div(
        position.absolute,
        transformOrigin := "0 0",
        transform <-- currentTransform.map(tr => s"translate(${tr.x}px,${tr.y}px) scale(${tr.k})"),
        height := "100%",


      ),
      div(
        position.absolute,
        transform <-- currentTransform.map(tr => s"translate(${tr.x}px,${tr.y}px) scale(${tr.k})"),
        for( i <- 0 to 30) yield {
          val xpos = (-(now % unit).toDouble / unit + i) * unitWidth
          verticalLine(xpos)(backgroundColor := "rgba(0,0,0,0.10)")
        },
        verticalLine(0)(backgroundColor := "steelblue", width := "2px"),

        Rx {
          bars().map { bar => 
            if(bar.startDate.isDefined && bar.endDate.isDefined)
              renderTask(state, bar, unit, unitWidth, now, parentId = focusState.focusedId) 
            else
              VDomModifier.empty
          }
        },
      )
    )

    )
  }

  def onD3Zoom(handle: Transform => Unit):VDomModifier = {
      managedElement.asHtml { elem =>
        val selection = d3.select(elem)
        val zoom = d3.zoom()
        zoom.on("zoom", {() =>
          handle(d3.event.transform)
        })

        selection.call(zoom)
        Cancelable(() => selection.on("zoom", null:ListenerFunction0))
      }
  }

  def verticalLine(xpos:Double) = {
    div(
      position.absolute,
      transform := s"translate(${xpos}px, 0px)",
      height := "500px",
      width := "1px",
    )
  }

  private def renderTask(state: GlobalState, bar: Bar, unit:Double, unitWidth:Double, now:EpochMilli, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {

    val xPosWidth:Option[(Long, Long)] = for {
      startDate <- bar.startDate
      endDate <- bar.endDate
      if startDate < endDate
    } yield (startDate - now, endDate - startDate)

    val barHeight = 32

    div(
      position.absolute,
      backgroundColor := "white",
      border := "1px solid black",
      padding := "5px",
      borderRadius := "2px",
    
      height := s"${barHeight}px",
      xPosWidth.map{ case (xPos, barWidth) =>
        VDomModifier(
          transform := s"translate(${xPos / unit * unitWidth}px, ${bar.yPos * barHeight}px)",
          width := s"${barWidth / unit * unitWidth}px"
        )
      },
      Styles.flex,
      justifyContent.spaceBetween,
      alignItems.flexStart,

      renderNodeData(bar.node.data),
    )
  }
}

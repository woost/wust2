package wust.webApp.views

import d3v4._
import org.scalajs.dom
import outwatch.reactive._
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra.transform
import rx._
import wust.webUtil.outwatchHelpers._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util.collection.BasicMap
import wust.webApp.state.{FocusState, GlobalState, Placeholder}
import wust.webApp.views.Components._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

// Timeline which uses properties for start and enddate
object GanttView {

  final case class Bar(node: Node, yPos: Int, startDate: Option[EpochMilli], endDate: Option[EpochMilli])

  def getFirstDate(propertyMap: BasicMap[String, List[PropertyData.PropertyValue]], search: String): Option[EpochMilli] = {
    propertyMap
      .get(search)
      .flatMap(_.collectFirst{
        case PropertyData.PropertyValue(_, Node.Content(_, data: NodeData.Date, _, _, _, _)) => data.content
      })
  }

  def extents(bars: Array[Bar]) = {
    var minStart = EpochMilli.max
    var maxEnd = EpochMilli.min
    for {
      bar <- bars
    } {
      bar.startDate.foreach { date =>
        if (date < minStart) minStart = date
      }
      bar.endDate.foreach { date =>
        if (date > maxEnd) maxEnd = date
      }
    }
    Array(minStart,maxEnd)
  }

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val barsRx: Rx[Array[Bar]] = Rx {
      val graph = GlobalState.graph()
      val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

      var i = 0
      graph.childrenIdx.flatMap(nodeIdx) { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        if (node.role == NodeRole.Task) {
          val propertyData = PropertyData.Single(graph, nodeIdx)
          val startDate: Option[EpochMilli] = getFirstDate(propertyData.info.propertyMap, "startdate")
          val endDate: Option[EpochMilli] = getFirstDate(propertyData.info.propertyMap, "enddate")
          val yPos = i
          i += 1
          Array(Bar(node, yPos, startDate, endDate))
        } else Array.empty[Bar]
      }
    }

    var zoomTransform: Var[d3.Transform] = Var(d3.zoomIdentity)
    val x = d3.scaleTime()
    x.range(js.Array(0, dom.window.innerWidth)) // will be corrected later
    barsRx.foreach { bars =>
      x.domain(extents(bars).map(new js.Date(_)).toJSArray)
    }
    val scaledX = Rx {
      zoomTransform().rescaleX(x)
    }
    val xAxis = d3.axisBottom(x)

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",

      Styles.flex,
      flexDirection.column,

      InputRow(
        
        Some(focusState),
        submitAction = { sub =>
          val newNode = Node.MarkdownTask(sub.text)
          val changes = GraphChanges.addNodesWithParents(newNode :: Nil, GlobalState.page.now.parentId.map(ParentId(_))) merge sub.changes(newNode.id)
          GlobalState.submitChanges(changes)
        },
        placeholder = Placeholder.newTask,
        showMarkdownHelp = true
      ),
      div(
        onDomMount.foreach { elem =>
          val width = elem.getBoundingClientRect().width
          x.range(js.Array(0, width))
        },
        backgroundColor := "rgba(255,255,255,0.5)",
        height := "100%",
        overflow.hidden,
        onD3Zoom(zoomTransform() = _),
        position.relative,

        svg.svg(
          width := "100%",
          height := "30px",
          Rx {
            svg.g(
              onDomMount.foreach { elem: dom.Element =>
                xAxis.scale(scaledX())(d3.select(elem))
              }
            )
          }
        ),
        div(
          Rx {
            barsRx().map { bar =>
              if (bar.startDate.isDefined && bar.endDate.isDefined) {
                renderTask( bar, scaledX(), parentId = focusState.focusedId)
              } else
                VDomModifier.empty
            }
          }
        )
      )

    )
  }

  def onD3Zoom(handle: d3.Transform => Unit): VDomModifier = {
    managedElement.asHtml { elem =>
      val selection = d3.select(elem)
      val zoom = d3.zoom()
      zoom.on("zoom", { () =>
        handle(d3.event.transform)
      })

      selection.call(zoom)
      Subscription(() => selection.on("zoom", null: ListenerFunction0))
    }
  }

  def verticalLine(xpos: Double) = {
    div(
      position.absolute,
      transform := s"translate(${xpos}px, 0px)",
      height := "500px",
      width := "1px",
    )
  }

  private def renderTask(bar: Bar, x: d3.TimeScale, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {

    val xPosWidth: Option[(Double, Double)] = for {
      startDate <- bar.startDate
      endDate <- bar.endDate
      if startDate < endDate
    } yield {
      val start = x(new js.Date(startDate))
      val end = x(new js.Date(endDate))
      (start, end - start)
    }

    val barHeight = 34

    div(
      position.absolute,
      backgroundColor := "white",
      border := "1px solid black",
      padding := "5px",
      borderRadius := "2px",

      height := s"${barHeight - 2}px",
      xPosWidth.map{
        case (xPos, barWidth) =>
          VDomModifier(
            transform := s"translate(${xPos}px, ${bar.yPos * barHeight}px)",
            width := s"${barWidth}px"
          )
      },
      Styles.flex,
      justifyContent.spaceBetween,
      alignItems.flexStart,

      renderNodeData( bar.node),
    )
  }
}

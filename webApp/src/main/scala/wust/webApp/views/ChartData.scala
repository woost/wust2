package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import colorado.RGB
import rx._
import wust.ids.NodeRole
import wust.sdk.{BaseColors, NodeColor}
import wust.webApp.state.{GlobalState, TraverseState}
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._

object ChartData {

  case class ChartDataContainer(label: String, labelColor: RGB, dataPoints: Double)
  case class ChartRenderData(rawCanvasData: Rx.Dynamic[Seq[ChartDataContainer]], steps: Option[Double] = None, chartLabel: String = "# Tasks", chartType: String = "bar") {

    def render(implicit ctx: Ctx.Owner): HtmlVNode = {
      import typings.chartDotJs.chartDotJsMod._
      import scala.scalajs.js
      import scala.scalajs.js.`|`
      import scala.scalajs.js.JSConverters._

      div(
        rawCanvasData.map { rawDataContainer =>
          val (chartLabels, chartPoints, chartColors) = {
            val data = rawDataContainer.unzip3 { rawData =>
              val labels: String | js.Array[String] = (rawData.label: String | js.Array[String])
              val points: js.UndefOr[ChartPoint | Double | Null] = js.defined[ChartPoint | Double | Null](rawData.dataPoints)
              (labels, points, rawData.labelColor)
            }
            (data._1.toJSArray, data._2.toJSArray, data._3.toJSArray)
          }

          val chartSteps = steps.getOrElse(math.max(math.ceil(rawDataContainer.map(_.dataPoints).max / 10), 1))

          Elements.chartCanvas {
            ChartConfiguration(
              `type` = chartType,
              data = new ChartData {
                labels = chartLabels
                datasets = js.Array(new ChartDataSets {
                  label = chartLabel
                  data = chartPoints
                  backgroundColor = chartColors.map(c => s"rgba(${ c.ri }, ${ c.gi }, ${ c.bi }, 0.2)")
                  borderColor = chartColors.map(_.toCSS)
                  borderWidth = 1.0
                })
              },
              options = new ChartOptions {
                scales = new ChartScales {
                  yAxes = js.Array(new ChartYAxe {
                    ticks = new TickOptions {
                      beginAtZero = true
                      stepSize = chartSteps
                    }
                  })
                }
              }
            )
          }
        },
      )
    }
  }

  def renderAssignedChart(traverseState: TraverseState)(implicit ctx: Ctx.Owner): HtmlVNode = {
    val rawCanvasData = Rx {
      val graph = GlobalState.graph()
      val nodeId = traverseState.parentId
      val users = graph.members(nodeId).map(_.id)
      val taskStats = AssignedTasksData.assignedTasksStats(graph, nodeId, users)

      taskStats.map(stats =>
        ChartDataContainer(
          label = stats.user.str,
          BaseColors.kanbanColumnBg.copy(h = NodeColor.hue(stats.user.id)).rgb,
          dataPoints = stats.numTasks
        )
      )
    }

    ChartRenderData(rawCanvasData).render
  }

  def renderStagesChart(traverseState: TraverseState)(implicit ctx: Ctx.Owner): HtmlVNode = {
    val rawCanvasData = Rx {
      val graph = GlobalState.graph()

      val uncategorizedColumn = ChartDataContainer(
        label = "Uncategorized",
        labelColor = BaseColors.kanbanColumnBg.rgb,
        dataPoints = KanbanData.inboxNodesCount(graph, traverseState)
      )

      //TODO: check whether a column hast nested stages
      uncategorizedColumn +: KanbanData.columnNodes(graph, traverseState).collect {
        case n if n._2 == NodeRole.Stage && !graph.isDoneStage(n._1) =>
          val node = graph.nodesByIdOrThrow(n._1 )
          val (stageName, stageChildren) = (node.str, graph.notDeletedChildrenIdx(graph.idToIdxOrThrow(node.id)).length: Double)

          ChartDataContainer(
            label = stageName,
            labelColor = BaseColors.kanbanColumnBg.copy(h = NodeColor.hue(node.id)).rgb,
            dataPoints = stageChildren
          )
      }
    }

    ChartRenderData(rawCanvasData).render
  }
}

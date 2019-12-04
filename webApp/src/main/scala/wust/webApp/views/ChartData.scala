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

  case class ChartDataContainer(label: String, labelColor: RGB, dataValue: Double)
  case class ChartRenderData(rawChartData: Rx.Dynamic[Seq[ChartDataContainer]], steps: Option[Double] = None, chartLabel: String = "# Tasks", chartType: String = "bar") {

    def render(implicit ctx: Ctx.Owner): HtmlVNode = {
      import typings.chartDotJs.chartDotJsMod._
      import scala.scalajs.js
      import scala.scalajs.js.`|`
      import scala.scalajs.js.JSConverters._

      div(
        rawChartData.map { rawDataContainer: Seq[ChartDataContainer] =>
          val (chartLabels, chartPoints, chartColors) = {
            val data = rawDataContainer.unzip3 { rawData =>
              val labels: String | js.Array[String] = (rawData.label: String | js.Array[String])
              val points: js.UndefOr[ChartPoint | Double | Null] = js.defined[ChartPoint | Double | Null](rawData.dataValue)
              (labels, points, rawData.labelColor)
            }
            (data._1.toJSArray, data._2.toJSArray, data._3.toJSArray)
          }

          val chartSteps = if(rawDataContainer.isEmpty) 1 else steps.getOrElse(math.max(math.ceil( rawDataContainer.map(_.dataValue).max / 10), 1))

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

      taskStats.flatMap(stats =>
        if(stats.numTasks > 0)
          Some(ChartDataContainer(
            label = stats.user.str,
            BaseColors.kanbanColumnBg.copy(h = NodeColor.hue(stats.user.id)).rgb,
            dataValue = stats.numTasks
          ))
        else
          None
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
        dataValue = KanbanData.inboxNodesCount(graph, traverseState)
      )

      //TODO: check whether a column hast nested stages
      uncategorizedColumn +: KanbanData.columnNodes(graph, traverseState).collect {
        case n if n._2 == NodeRole.Stage && !graph.isDoneStage(n._1) =>
          val node = graph.nodesByIdOrThrow(n._1 )
          val (stageName, stageChildren) = (node.str, graph.notDeletedChildrenIdx(graph.idToIdxOrThrow(node.id)).length: Double)

          ChartDataContainer(
            label = stageName,
            labelColor = BaseColors.kanbanColumnBg.copy(h = NodeColor.hue(node.id)).rgb,
            dataValue = stageChildren
          )
      }
    }

    ChartRenderData(rawCanvasData).render
  }

  def renderDeadlineChart(traverseState: TraverseState, deepSearch: Var[Boolean])(implicit ctx: Ctx.Owner): HtmlVNode = {
    val rawCanvasData = Rx {
      val graph = GlobalState.graph()

      val baseColors = Array(
        RGB(255, 99, 132),  // Red
        RGB(255, 159, 64),  // Orange
        RGB(255, 206, 86),  // Yellow
        RGB(54, 162, 235),  // Blue
        RGB(75, 192, 192),  // Green
      )

      val buckets = AssignedTasksData.TimeBucket.defaultBuckets
      AssignedTasksData.assignedTasks(graph, traverseState.parentId, None, buckets, deepSearch()).dueTasks.zipWithIndex.map {
        case (dueDates, dueTaskBucketsIdx) =>
          ChartDataContainer(
            label = buckets(dueTaskBucketsIdx).name,
            labelColor = baseColors(dueTaskBucketsIdx),
            dataValue = dueDates.size
          )
      }
    }
    ChartRenderData(rawCanvasData).render
  }
}

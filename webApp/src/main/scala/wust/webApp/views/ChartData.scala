package wust.webApp.views

import outwatch._
import outwatch.dsl._
import colorado.RGB
import colibri.ext.rx._
import rx._
import wust.ids.NodeRole
import wust.sdk.{BaseColors, NodeColor}
import wust.webApp.state.{GlobalState, TraverseState}
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._

object ChartData {

  case class LabelColor(front: RGB, background: Option[RGB] = None)
  case class ChartDataContainer(label: String, dataValue: Double, color: LabelColor)
  case class ChartRenderData(rawChartData: Rx[Seq[ChartDataContainer]], steps: Option[Double] = None, chartLabel: String = "# Tasks", chartType: String = "bar") {

    def render(implicit ctx: Ctx.Owner): HtmlVNode = {
      import typings.chartJs.mod._
      import scala.scalajs.js
      import scala.scalajs.js.`|`
      import scala.scalajs.js.JSConverters._

      div(
        rawChartData.map { rawDataContainer: Seq[ChartDataContainer] =>
          val (chartLabels, chartPoints, chartColors) = {
            val data = rawDataContainer.unzip3 { rawData =>
              val labels: String | js.Array[String] = (rawData.label: String | js.Array[String])
              val points: js.UndefOr[ChartPoint | Double | Null] = js.defined[ChartPoint | Double | Null](rawData.dataValue)
              (labels, points, rawData.color)
            }
            (data._1.toJSArray, data._2.toJSArray, data._3.toJSArray)
          }

          val chartSteps = if(rawDataContainer.isEmpty) 1 else steps.getOrElse(math.max(math.ceil( rawDataContainer.map(_.dataValue).max / 10), 1))

          // TODO: Structure code like in this demo:
          // https://github.com/ScalablyTyped/Demos/blob/master/chart/src/main/scala/demo/Demo.scala

          val config = ChartConfiguration()
          val data = typings.chartJs.mod.ChartData()
          data.labels = chartLabels
          val dataSets = ChartDataSets()
          dataSets.label = chartLabel
          dataSets.data = chartPoints
          dataSets.backgroundColor = chartColors.map {
            case c@LabelColor(_, None) => c.front.toCSS(a = 0.2)
            case c@LabelColor(_, Some(bgColor)) => bgColor.toCSS(0.2)
          }
          dataSets.borderColor = chartColors.map(_.front.toCSS)
          dataSets.borderWidth = 1.0
          data.datasets = js.Array(dataSets)
          val yAxes = CommonAxe()
          yAxes.ticks = TickOptions()
          // yAxes.ticks.beginAtZero = true
          // yAxes.ticks.stepSize = chartSteps
          val scales = ChartScales()
          scales.yAxes = js.Array(yAxes)
          val options = ChartOptions()
          options.scales = scales
          config.`type` = chartType
          config.data = data
          config.options = options
          Elements.chartCanvas(config)
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
            dataValue = stats.numTasks,
            color = LabelColor(NodeColor.kanbanColumnBg.rgbOf(stats.user))
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
        dataValue = KanbanData.inboxNodesCount(graph, traverseState),
        color = LabelColor(BaseColors.kanbanColumnBg.rgb),
      )

      //TODO: check whether a column hast nested stages
      uncategorizedColumn +: KanbanData.columnNodes(graph, traverseState).collect {
        case n if n._2 == NodeRole.Stage && !graph.isDoneStage(n._1) =>
          val node = graph.nodesByIdOrThrow(n._1 )
          val (stageName, stageChildren) = (node.str, graph.notDeletedChildrenIdx(graph.idToIdxOrThrow(node.id)).length: Double)

          ChartDataContainer(
            label = stageName,
            dataValue = stageChildren,
            color = LabelColor(NodeColor.kanbanColumnBg.rgbOf(node)),
          )
      }
    }

    ChartRenderData(rawCanvasData).render
  }

  def renderDeadlineChart(assignedTasks: Rx[AssignedTasksData.AssignedTasks])(implicit ctx: Ctx.Owner): HtmlVNode = {
    val rawCanvasData = assignedTasks.map { allTasks =>
      val buckets = DueDate.Bucket.values
      allTasks.dueTasks.zipWithIndex.map {
        case (dueDates, dueTaskBucketsIdx) =>
          ChartDataContainer(
            label = buckets(dueTaskBucketsIdx).name,
            dataValue = dueDates.size,
            color = LabelColor(buckets(dueTaskBucketsIdx).bgColor)
          )
      }
    }
    ChartRenderData(rawCanvasData).render
  }
}

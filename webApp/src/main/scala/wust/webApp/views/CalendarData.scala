package wust.webApp.views

import wust.css.ZIndex
import wust.sdk.Colors
import d3v4._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra.transform
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webUtil.Elements._
import wust.facades.dateFns
import wust.facades.dateFns.DateFns
import wust.webApp.state.{ FocusState, GlobalState, Placeholder }
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._
import collection.mutable
import collection.immutable

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import fontAwesome.freeSolid

object CalendarData {
  case class Event(startDate: js.Date, endDate: js.Date, node: Node)
  case class EventChunk(startDate: js.Date, weekDay: Int, width: Int, level: Int, node: Node, start: Boolean = true, end: Boolean = true)
  case class CalendarData(weeklyEventChunks: collection.Map[String, Vector[EventChunk]], weekHeight: collection.Map[String, Int])
  object CalendarData {
    val empty = new CalendarData(Map.empty, Map.empty)
  }
  def weekKey(date: js.Date) = DateFns.format(date, "YYYYww", js.Dynamic.literal(useAdditionalWeekYearTokens = true))
  def dayKey(date: js.Date) = DateFns.format(date, "yyyyMMdd")

  private def eventsToChunks(unsortedEvents: Seq[Event]): CalendarData = {
    // There are two main Problems to be solved here:
    // 1) break up events into chunks: since an event can cover more than one week, it needs to be broken up for rendering.
    // 2) chunks covering the same day overlap and need to be shifted vertically
    val events = unsortedEvents.sortBy(event => event.startDate.getUTCMilliseconds())
    val weeklyEventChunks = mutable.HashMap.empty[String, Vector[EventChunk]].withDefaultValue(Vector.empty)

    // vertically displace overlapping events
    val weekHeight = mutable.HashMap.empty[String, Int].withDefaultValue(0) // minimum height of week row to contain all events
    val dayFill = mutable.HashMap.empty[String, immutable.BitSet].withDefault(_ => immutable.BitSet.empty) // available space per day

    events.foreach {
      case Event(startDate, endDate, node) =>
        val weekStarts = DateFns.eachWeekOfInterval(new dateFns.Interval { var start = startDate; var end = endDate }) // all weeks covered by the event

        val startWeekDay = DateFns.getDay(startDate)
        val endWeekDay = DateFns.getDay(endDate)

        weekStarts.zipWithIndex.foreach {
          case (week, i) =>
            val containsStart = i == 0
            val containsEnd = i == (weekStarts.length - 1)
            val fromWeekDay = if (containsStart) startWeekDay else 0
            val toWeekDay = if (containsEnd) (endWeekDay + 1) else 7 // exclusive
            val startDate = DateFns.addDays(week, fromWeekDay)

            // find lowest available level on start day
            val dKey = dayKey(startDate)
            val chunkLevel = {
              var i = 0
              val fill = dayFill(dKey)
              while (fill(i)) i += 1
              i
            }

            // update week row heights
            val wKey = weekKey(week)
            weekHeight(wKey) = weekHeight(wKey) max (chunkLevel + 1)

            // update available spaces for the following event days
            for (eventDay <- fromWeekDay until toWeekDay) {
              dayFill(dayKey(DateFns.addDays(week, eventDay))) += chunkLevel
            }

            weeklyEventChunks(weekKey(week)) :+= EventChunk(startDate = startDate, weekDay = fromWeekDay, width = toWeekDay - fromWeekDay, level = chunkLevel, start = containsStart, end = containsEnd, node = node)
        }
    }

    CalendarData(weeklyEventChunks, weekHeight)
  }

  def getCalendarData(graph: Graph, parentId: NodeId): CalendarData = {
    val parentIdxOpt = graph.idToIdx(parentId)
    parentIdxOpt.fold(CalendarData.empty){ parentIdx =>
      val eventBuilder = Array.newBuilder[Event]
      graph.childrenIdx.foreachElement(parentIdx) { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        if (node.role == NodeRole.Task) {
          val props = PropertyData.getProperties(graph, nodeIdx)
          props.get(EdgeData.LabeledProperty.dueDate.key).foreach { propValues =>
            propValues.foreach { propValue =>
              propValue.node.data match {
                case NodeData.DateTime(dueDate) =>
                  val startDate = new js.Date(dueDate)
                  eventBuilder += Event(startDate = startDate, endDate = startDate, node = node)
                case NodeData.Date(dueDate) =>
                  val startDate = new js.Date(dueDate)
                  eventBuilder += Event(startDate = startDate, endDate = startDate, node = node)
                case _ =>
              }
            }
          }
        }
      }
      eventsToChunks(eventBuilder.result())
    }
  }
}

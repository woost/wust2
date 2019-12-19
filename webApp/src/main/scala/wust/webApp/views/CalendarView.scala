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

object CalendarView {
  case class Event(startDate: js.Date, endDate: js.Date, node: Node)
  case class EventChunk(startDate: js.Date, weekDay: Int, width: Int, level: Int, node: Node, start: Boolean = true, end: Boolean = true)
  case class CalendarData(weeklyEventChunks: collection.Map[String,Vector[EventChunk]], weekHeight: collection.Map[String,Int])
  private def weekKey(date: js.Date) = DateFns.format(date, "YYYYww", js.Dynamic.literal(useAdditionalWeekYearTokens = true))
  private def dayKey(date: js.Date) = DateFns.format(date, "yyyyMMdd")

  val weekDays = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") //TODO: localized
  val gridSpacing = 3
  val eventLineHeight = 30

  private def eventsToChunks(unsortedEvents: Seq[Event]):CalendarData = {
    // There are two main Problems to be solved here:
    // 1) break up events into chunks: since an event can cover more than one week, it needs to be broken up for rendering.
    // 2) chunks covering the same day overlap and need to be shifted vertically
    val events = unsortedEvents.sortBy(event => event.startDate.getUTCMilliseconds())
    val weeklyEventChunks = mutable.HashMap.empty[String,Vector[EventChunk]].withDefaultValue(Vector.empty)

    // vertically displace overlapping events
    val weekHeight = mutable.HashMap.empty[String,Int].withDefaultValue(2) // how high one week row needs to be
    val dayFill = mutable.HashMap.empty[String,immutable.BitSet].withDefault(_ => immutable.BitSet.empty) // available space per day

    events.foreach { case Event(startDate, endDate, node) =>
      val weekStarts = DateFns.eachWeekOfInterval(new dateFns.Interval{ var start = startDate; var end = endDate}) // all weeks covered by the event

      val startWeekDay = DateFns.getDay(startDate)
      val endWeekDay = DateFns.getDay(endDate)

      weekStarts.zipWithIndex.foreach { case (week, i) =>
        val containsStart = i == 0
        val containsEnd = i == (weekStarts.length-1)
        val fromWeekDay = if(containsStart) startWeekDay else 0
        val toWeekDay = if(containsEnd) (endWeekDay+1) else 7 // exclusive
        val startDate = DateFns.addDays(week, fromWeekDay)

        // find lowest available level on start day
        val dKey = dayKey(startDate)
        val chunkLevel = {
          var i = 0
          val fill = dayFill(dKey)
          while( fill(i) ) i += 1 
          i
        }

        // update week row heights
        val wKey = weekKey(week)
        weekHeight(wKey) = weekHeight(wKey) max (chunkLevel+1)

        // update available spaces for the following event days
        for(eventDay <- fromWeekDay until toWeekDay) {
          dayFill(dayKey(DateFns.addDays(week, eventDay))) += chunkLevel
        }

        weeklyEventChunks(weekKey(week)) :+= EventChunk(startDate = startDate, weekDay = fromWeekDay,width = toWeekDay - fromWeekDay, level = chunkLevel, start = containsStart, end = containsEnd, node = node)
      }
    }

    CalendarData(weeklyEventChunks, weekHeight)
  }


  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    // TODO: custom weekstart: Monday/Sunday

    val initialSelectedDate = new js.Date()
    val selectedDate: Var[js.Date] = Var(initialSelectedDate)
    val weeksInMonth: Rx[Int] = Rx { DateFns.getWeeksInMonth(selectedDate()) } // TODO: ,{ weekStartsOn: 1 }
    val daysInMonth: Rx[Int] = Rx { DateFns.getDaysInMonth(selectedDate()) }
    val selectedMonth: Rx[Int] = Rx { DateFns.getMonth(selectedDate()) }
    val weekDayOfFirstDayInMonth: Rx[Int] = Rx{ DateFns.getDay(DateFns.setDate(selectedDate(), 1)) }



    val calendarData:Rx[CalendarData] = Rx {
      val graph = GlobalState.graph()
      val events = List(
        //
        //
        //
        //TODO: extract dates from nodes
        //
        //
        //
        Event(startDate = new js.Date(2019, 10, 27), endDate = new js.Date(2019, 10, 28), Node.MarkdownTask("Frosch")),
        Event(startDate = new js.Date(2019, 10, 28), endDate = new js.Date(2019, 11, 1),  Node.MarkdownTask("Bobin")),
        Event(startDate = new js.Date(2019, 11, 2), endDate = new js.Date(2019, 11, 4),   Node.MarkdownTask("Gorin")),
        Event(startDate = new js.Date(2019, 11, 6), endDate = new js.Date(2019, 11, 10),  Node.MarkdownTask("Hobin")),
        Event(startDate = new js.Date(2019, 11, 13), endDate = new js.Date(2019, 11, 14), Node.MarkdownTask("Florin")),
        Event(startDate = new js.Date(2019, 11, 17), endDate = new js.Date(2019, 11, 18), Node.MarkdownTask("Greugen")),
        Event(startDate = new js.Date(2019, 11, 18), endDate = new js.Date(2019, 11, 19), Node.MarkdownTask("Aunutz")),
        Event(startDate = new js.Date(2019, 11, 19), endDate = new js.Date(2019, 11, 20), Node.MarkdownTask("Blagon")),
        Event(startDate = new js.Date(2019, 11, 20), endDate = new js.Date(2019, 11, 20), Node.MarkdownTask("Raffal")),
        Event(startDate = new js.Date(2019, 11, 31), endDate = new js.Date(2020, 0, 2),   Node.MarkdownTask("Heisen")),
        Event(startDate = new js.Date(2020, 0, 4), endDate = new js.Date(2020, 0, 5),     Node.MarkdownTask("Klünki")),
      )
      eventsToChunks(events)
    }

    div(
      padding := "20px",
      Styles.flex,
      flexDirection.column,
      div(
        Styles.flex,
        alignItems.center,
        Rx {
          div(fontSize := "20px", minWidth := "150px", DateFns.format(selectedDate(), "MMMM yyyy"))
        },
        button(cls := "ui compact basic button", freeSolid.faCaretLeft, onClickDefault.useLazy(DateFns.subMonths(selectedDate.now, 1)) --> selectedDate),
        button(cls := "ui compact basic button", "Today", onClickDefault.useLazy(new js.Date()) --> selectedDate),
        button(cls := "ui compact basic button", freeSolid.faCaretRight, onClickDefault.useLazy(DateFns.addMonths(selectedDate.now, 1)) --> selectedDate),
        marginBottom := "20px",
      ),
      div(
        marginBottom := "20px",
      ),
      div(
        Styles.flexStatic,
        Styles.flex,
        opacity := 0.4,
        fontWeight.bold,
        List.tabulate(7)(i => div(flex := "1", marginLeft := s"${gridSpacing}px", padding := "5px", weekDays(i))),
      ),
      div(
        Styles.flexStatic,
        Styles.flex,
        flexDirection.column,
        flexGrow := 1,
        Rx {
          List.tabulate(weeksInMonth()){weekOfMonth =>
            val firstWeekDayOfMonth = (weekOfMonth * 7) - weekDayOfFirstDayInMonth() + 1 // first day of every week row
            val week = DateFns.setDate(selectedDate(), firstWeekDayOfMonth)
            val wKey = weekKey(week)
            div(
              height := s"${(1+calendarData().weekHeight(wKey)) * eventLineHeight + gridSpacing}px",
              Styles.flexStatic,
              Styles.flex,
              List.tabulate(7){ weekDay =>
                val relativeDayOfMonth = firstWeekDayOfMonth + weekDay
                val dateOfCell = DateFns.setDate(selectedDate(), relativeDayOfMonth)
                val dayOfMonth = DateFns.getDate(dateOfCell)
                val monthOfCell = DateFns.getMonth(dateOfCell)
                val isToday = DateFns.isSameDay(dateOfCell, new js.Date())
                div(
                  flex := "1",
                  VDomModifier.ifTrue(monthOfCell == selectedMonth())(
                    backgroundColor := Colors.contentBgShade
                  ),
                marginBottom := s"${gridSpacing}px",
                marginRight := s"${gridSpacing}px",
                div(
                  if (relativeDayOfMonth < 1 || relativeDayOfMonth > daysInMonth())
                    VDomModifier(
                      DateFns.format(dateOfCell, "d MMM"),
                      opacity := 0.4,
                      )
                  else
                    VDomModifier(
                      dayOfMonth,
                      opacity := 0.7,
                      ),
                    margin := "5px 10px",
                  ),

                  VDomModifier.ifTrue(isToday)(boxShadow := "0 0 0px 2px rgb(242, 107, 77)"),
                  borderRadius := "2px",
                )
              },

              position.relative,
              calendarData().weeklyEventChunks(wKey).map { chunk =>
                renderEvent(chunk)
              }
          )}
        },

      ),
      div(
        // padding-bottom flexbox hack
        Styles.flexStatic,
        height := "20px",
      ),
    ),
  }

  private def renderEvent(chunk: EventChunk) = {
    @inline def eventBorderRadius = "5px"
      div(
      //TODO: overflow ellipsis
      chunk.node.str,
      // s" (${DateFns.format(chunk.startDate, "MM-dd")} ${chunk.weekDay}:${chunk.width})",
      position.absolute,
      left := s"${(100.0 / 7) * chunk.weekDay}%",
      top := s"${(chunk.level+1)*eventLineHeight}px",
      width := s"calc(${(100.0 / 7) * chunk.width}% - ${gridSpacing}px)",
      if(chunk.start) VDomModifier(borderTopLeftRadius := eventBorderRadius, borderBottomLeftRadius := eventBorderRadius)
      else VDomModifier(borderTopLeftRadius := "0px", borderBottomLeftRadius := "0px"),
      if(chunk.end) VDomModifier(borderTopRightRadius := eventBorderRadius, borderBottomRightRadius := eventBorderRadius)
      else VDomModifier(borderTopRightRadius := "0px", borderBottomRightRadius := "0px"),
      padding := "4px 8px",
      cls := "nodecard node",
    )
  }

}

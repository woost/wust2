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
  val weekDays = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") //TODO: localized
  case class EventChunk(startDate: js.Date, weekDay: Int, width: Int, level: Int, node: Node, start: Boolean = true, end: Boolean = true)
  val gridSpacing = 3
  val eventLineHeight = 30

  case class Event(startDate: js.Date, endDate: js.Date, node: Node)
  private def weekKey(date: js.Date) = DateFns.format(date, "YYYYww", js.Dynamic.literal(useAdditionalWeekYearTokens = true))
  private def dayKey(date: js.Date) = DateFns.format(date, "yyyyMMdd")
  private def eventsToChunks(unsortedEvents: Seq[Event]):(collection.Map[String,Vector[EventChunk]], collection.Map[String,Int], collection.Map[String, immutable.BitSet]) = {
    // There are two main Problems to be solved:
    // 1) break up events into chunks which can be 
    val events = unsortedEvents.sortBy(event => event.startDate.getUTCMilliseconds())
    val weeks = mutable.HashMap.empty[String,Vector[EventChunk]].withDefaultValue(Vector.empty)

    // vertically displace overlapping events
    val weekMaxLevels = mutable.HashMap.empty[String,Int].withDefaultValue(1)
    val dayFill = mutable.HashMap.empty[String,immutable.BitSet].withDefault(_ => immutable.BitSet.empty)

    events.foreach { case Event(startDate, endDate, node) =>
      val weekStarts = DateFns.eachWeekOfInterval(new dateFns.Interval{ var start = startDate; var end = endDate})

      val startWeekDay = DateFns.getDay(startDate)
      val endWeekDay = DateFns.getDay(endDate)

      weekStarts.zipWithIndex.foreach { case (week, i) =>
        val containsStart = i == 0
        val containsEnd = i == (weekStarts.length-1)
        val fromWeekDay = if(containsStart) startWeekDay else 0
        val toWeekDay = if(containsEnd) (endWeekDay+1) else 7 // exclusive
        val startDate = DateFns.addDays(week, fromWeekDay)

        val dKey = dayKey(startDate)
        val chunkLevel = {
          // find lowest free level
          var i = 0
          val fill = dayFill(dKey)
          while( fill(i) ) i += 1 
          i
        }

        val wKey = weekKey(week)
        weekMaxLevels(wKey) = weekMaxLevels(wKey) max (chunkLevel+1)

        for(eventDay <- fromWeekDay until toWeekDay) {
          dayFill(dayKey(DateFns.addDays(week, eventDay))) += chunkLevel
        }

        weeks(weekKey(week)) :+= EventChunk(startDate = startDate, weekDay = fromWeekDay,width = toWeekDay - fromWeekDay, level = chunkLevel, start = containsStart, end = containsEnd, node = node)
      }
    }

    (weeks, weekMaxLevels, dayFill)
  }


  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    // TODO: custom weekstart: Monday/Sunday

    val initialSelectedDate = new js.Date()
    val selectedDate: Var[js.Date] = Var(initialSelectedDate)
    val weeksInMonth: Rx[Int] = Rx { DateFns.getWeeksInMonth(selectedDate()) } // TODO: ,{ weekStartsOn: 1 }
    val daysInMonth: Rx[Int] = Rx { DateFns.getDaysInMonth(selectedDate()) }
    val selectedMonth: Rx[Int] = Rx { DateFns.getMonth(selectedDate()) }
    val weekDayOfFirstDayInMonth: Rx[Int] = Rx{ DateFns.getDay(DateFns.setDate(selectedDate(), 1)) }



      val (eventChunks,weekMaxLevels, dayFill) = eventsToChunks(List(
        Event(startDate = new js.Date(2019, 10, 27), endDate = new js.Date(2019, 10, 28), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 10, 28), endDate = new js.Date(2019, 11, 1), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 2), endDate = new js.Date(2019, 11, 4), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 6), endDate = new js.Date(2019, 11, 10), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 13), endDate = new js.Date(2019, 11, 14), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 17), endDate = new js.Date(2019, 11, 18), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 18), endDate = new js.Date(2019, 11, 19), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 19), endDate = new js.Date(2019, 11, 20), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 20), endDate = new js.Date(2019, 11, 20), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2019, 11, 31), endDate = new js.Date(2020, 0, 2), Node.MarkdownTask("Bloo")),
        Event(startDate = new js.Date(2020, 0, 4), endDate = new js.Date(2020, 0, 5), Node.MarkdownTask("Bloo")),
      ))

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
            val firstWeekDayOfMonth = (weekOfMonth * 7) - weekDayOfFirstDayInMonth() + 1
            val week = DateFns.setDate(selectedDate(), firstWeekDayOfMonth)
            val wKey = weekKey(week)
            div(
              height := s"${(1+weekMaxLevels(wKey)) * eventLineHeight + gridSpacing}px",
              Styles.flexStatic,
              Styles.flex,
              List.tabulate(7){ weekDay =>
                val relativeDayOfMonth = firstWeekDayOfMonth + weekDay
                val dateOfCell = DateFns.setDate(selectedDate(), relativeDayOfMonth)
                val fill = dayFill(dayKey(dateOfCell))
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
                    // s" (${fill.mkString(",")})"
                  ),

                  VDomModifier.ifTrue(isToday)(boxShadow := "0 0 0px 2px rgb(242, 107, 77)"),
                  borderRadius := "2px",
                )
              },

              position.relative,
              eventChunks(wKey).map { chunk =>
                event(chunk)
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

  val eventBorderRadius = "5px"
  def event(chunk: EventChunk) = div(
    //TODO: overflow ellipsis
    chunk.node.str,
    // s" (${DateFns.format(chunk.startDate, "MM-dd")} ${chunk.weekDay}:${chunk.width})",
    position.absolute,
    left := s"${(100.0 / 7) * chunk.weekDay}%",
    top := s"${(chunk.level+1)*eventLineHeight}px",
    width := s"calc(${(100.0 / 7) * chunk.width}% - ${gridSpacing}px)",
    backgroundColor := "#00aefd",
    color := "white",
    fontWeight.bold,
    VDomModifier.ifTrue(chunk.start)(borderTopLeftRadius := eventBorderRadius, borderBottomLeftRadius := eventBorderRadius),
    VDomModifier.ifTrue(chunk.end)(borderTopRightRadius := eventBorderRadius, borderBottomRightRadius := eventBorderRadius),
    padding := "4px 8px",
  )

}

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

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import fontAwesome.freeSolid

object CalendarView {
  val weekDays = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") //TODO: localized
  case class EventChunk(weekDay: Int, width: Int, level: Int, node: Node, start: Boolean = true, end: Boolean = true)
  val gridSpacing = 3
  val eventLineHeight = 30

  case class Event(startDate: js.Date, endDate: js.Date, node: Node)
  private def weekKey(date: js.Date) = DateFns.format(date, "YYYYww", js.Dynamic.literal(useAdditionalWeekYearTokens = true))
  private def eventsToChunks(events: Seq[Event]):collection.Map[String,Vector[EventChunk]] = {
    val weeks = mutable.HashMap.empty[String,Vector[EventChunk]].withDefaultValue(Vector())

    events.foreach { case Event(startDate, endDate, node) =>
      val weekStarts = DateFns.eachWeekOfInterval(new dateFns.Interval{ var start = startDate; var end = endDate})

      val startWeekDay = DateFns.getDay(startDate)
      val endWeekDay = DateFns.getDay(endDate)

      weekStarts.zipWithIndex.foreach { case (week, i) =>
        val containsStart = i == 0
        val containsEnd = i == (weekStarts.length-1)
        val fromWeekDay = if(containsStart) startWeekDay else 0
        val toWeekDay = if(containsEnd) endWeekDay else 6

        weeks(weekKey(week)) :+= EventChunk(weekDay = fromWeekDay,width = toWeekDay - fromWeekDay, level = 0, start = containsStart, end = containsEnd, node = node)
      }
    }

    weeks
  }


  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    // TODO: custom weekstart: Monday/Sunday

    val initialSelectedDate = new js.Date()
    val selectedDate: Var[js.Date] = Var(initialSelectedDate)
    val weeksInMonth: Rx[Int] = Rx { DateFns.getWeeksInMonth(selectedDate()) } // TODO: ,{ weekStartsOn: 1 }
    val daysInMonth: Rx[Int] = Rx { DateFns.getDaysInMonth(selectedDate()) }
    val selectedMonth: Rx[Int] = Rx { DateFns.getMonth(selectedDate()) }
    val weekDayOfFirstDayInMonth: Rx[Int] = Rx{ DateFns.getDay(DateFns.setDate(selectedDate(), 1)) }



    val weekLevels = Array(1, 1, 2, 1, 0, 0, 0)
    val eventChunks = eventsToChunks(List(
      Event(startDate = new js.Date(2019, 11, 2), endDate = new js.Date(2019, 11, 4), Node.MarkdownTask("Bloo"))
      ))
    // println(eventChunks.mkString("\n"))

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
        opacity := 0.5,
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
            div(
              height := s"${(1+weekLevels(weekOfMonth)) * eventLineHeight + gridSpacing}px",
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
                    backgroundColor := "gray"//Colors.contentBgShade
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
                  borderRadius := "3px",
                  )
              },

              position.relative,
              eventChunks(weekKey(week)).map { chunk =>
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

  def event(chunk: EventChunk) = div(
    //TODO: overflow ellipsis
    chunk.node.str,
    position.absolute,
    left := s"calc(${(100.0 / 7) * chunk.weekDay}% + ${(chunk.weekDay-1)*gridSpacing}px)",
    top := s"${(chunk.level+1)*eventLineHeight}px",
    width := s"calc(${(100.0 / 7) * chunk.width}% - ${gridSpacing}px)",
    backgroundColor := "#00aefd",
    color := "white",
    fontWeight.bold,
    VDomModifier.ifTrue(chunk.start)(borderTopLeftRadius := "3px", borderBottomLeftRadius := "3px"),
    VDomModifier.ifTrue(chunk.end)(borderTopRightRadius := "3px", borderBottomRightRadius := "3px"),
    padding := "5px",
  )

}

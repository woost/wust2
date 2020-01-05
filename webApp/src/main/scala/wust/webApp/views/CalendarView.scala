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
import wust.webApp.views.DragComponents.{ drag, registerDragContainer }
import wust.webApp.dragdrop.{DragItem, _}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import fontAwesome.freeSolid
import CalendarData._

object CalendarView {
  val weekDays = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") //TODO: localized
  val gridSpacing = 3
  val eventLineHeight = 30
  val minWeekHeight = 1

  case class MonthInfo(selectedDate: js.Date) {
    val weeksInMonth: Int = DateFns.getWeeksInMonth(selectedDate) // TODO: ,{ weekStartsOn: 1 }
    val daysInMonth: Int = DateFns.getDaysInMonth(selectedDate)
    val selectedMonth: Int = DateFns.getMonth(selectedDate)
    val weekDayOfFirstDayInMonth: Int = DateFns.getDay(DateFns.setDate(selectedDate, 1))
  }
  case class WeekInfo(weekOfMonth: Int, monthInfo: MonthInfo) {
    val firstWeekDayOfMonth: Int = (weekOfMonth * 7) - monthInfo.weekDayOfFirstDayInMonth + 1 // first day of every week row
    val week: js.Date = DateFns.setDate(monthInfo.selectedDate, firstWeekDayOfMonth)
    val key: String = weekKey(week)
  }

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    // TODO: custom weekstart: Monday/Sunday

    val initialSelectedDate = new js.Date()
    val selectedDate: Var[js.Date] = Var(initialSelectedDate)
    val monthInfo: Rx[MonthInfo] = Rx { MonthInfo(selectedDate()) }

    val calendarData: Rx[CalendarData] = Rx {
      getCalendarData(GlobalState.graph(), focusState.focusedId)
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
        button(freeSolid.faCaretLeft, cls := "ui compact basic button", onClickDefault.useLazy(DateFns.subMonths(selectedDate.now, 1)) --> selectedDate),
        button("Today", cls := "ui compact basic button", onClickDefault.useLazy(new js.Date()) --> selectedDate),
        button(freeSolid.faCaretRight, cls := "ui compact basic button", onClickDefault.useLazy(DateFns.addMonths(selectedDate.now, 1)) --> selectedDate),
        marginBottom := "20px",
      ),
      div(
        marginBottom := "20px",
      ),
      renderCalendar(monthInfo, calendarData),
      div(
        // padding-bottom flexbox hack
        Styles.flexStatic,
        height := "20px",
      ),
    )
  }

  private def renderCalendar(monthInfo: Rx[MonthInfo], calendarData: Rx[CalendarData])(implicit ctx: Ctx.Owner) = VDomModifier(
    renderWeekHeader(),
    div(
      Styles.flexStatic,
      Styles.flex,
      flexDirection.column,
      flexGrow := 1,
      Rx {
        List.tabulate(monthInfo().weeksInMonth){ weekOfMonth =>
          renderWeekRow(weekOfMonth, monthInfo(), calendarData)
        }
      },

      registerDragContainer(DragContainer.Calendar),
    ),
  )

  private def renderWeekHeader() = {
    div(
      Styles.flexStatic,
      Styles.flex,
      opacity := 0.4,
      fontWeight.bold,
      List.tabulate(7)(i =>
        div(weekDays(i), flex := "1", marginLeft := s"${gridSpacing}px", padding := "5px", overflow.hidden)),
    )
  }

  private def renderWeekRow(weekOfMonth: Int, monthInfo: MonthInfo, calendarData: Rx[CalendarData])(implicit ctx: Ctx.Owner) = {
    import monthInfo._
    val weekInfo = WeekInfo(weekOfMonth, monthInfo)
    div(
      flex := "1",
      Styles.flexStatic,
      minHeight <-- Rx{ s"${(1 + minWeekHeight.max(calendarData().weekHeight(weekInfo.key))) * eventLineHeight + gridSpacing}px" },

      Styles.flex,
      List.tabulate(7){ weekDay =>
        renderDayCell(weekDay, monthInfo, weekInfo)
      },

      // events are absolutely positioned in each week row:
      position.relative,
      Rx{
        calendarData().weeklyEventChunks(weekInfo.key).map { chunk =>
          renderEvent(chunk)
        }
      }
    )
  }

  private def renderDayCell(weekDay: Int, monthInfo: MonthInfo, weekInfo: WeekInfo)(implicit ctx: Ctx.Owner) = Rx {
    import monthInfo._
    import weekInfo._
    val relativeDayOfMonth = firstWeekDayOfMonth + weekDay
    val dateOfCell = DateFns.setDate(selectedDate, relativeDayOfMonth)
    val dayOfMonth = DateFns.getDate(dateOfCell)
    val monthOfCell = DateFns.getMonth(dateOfCell)
    val isToday = DateFns.isSameDay(dateOfCell, new js.Date())
    div(
      flex := "1",
      VDomModifier.ifTrue(monthOfCell == selectedMonth)(
        backgroundColor := Colors.contentBgShade
      ),
      marginBottom := s"${gridSpacing}px",
      marginRight := s"${gridSpacing}px",
      div(
        if (relativeDayOfMonth < 1 || relativeDayOfMonth > daysInMonth)
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
      overflow.hidden,
      drag(target = DragItem.CalendarDay(dateOfCell)),
    )
  }

  private def renderEvent(chunk: EventChunk) = {
    @inline def eventBorderRadius = "5px"
    div(
      chunk.node.str,
      Styles.cropEllipsis,
      padding := "4px 8px",
      cls := "nodecard node",

      position.absolute,
      left := s"${(100.0 / 7) * chunk.weekDay}%",
      top := s"${(chunk.level + 1) * eventLineHeight}px",
      width := s"calc(${(100.0 / 7) * chunk.width}% - ${gridSpacing}px)",

      if (chunk.start) VDomModifier(borderTopLeftRadius := eventBorderRadius, borderBottomLeftRadius := eventBorderRadius)
      else VDomModifier(borderTopLeftRadius := "0px", borderBottomLeftRadius := "0px"),
      if (chunk.end) VDomModifier(borderTopRightRadius := eventBorderRadius, borderBottomRightRadius := eventBorderRadius)
      else VDomModifier(borderTopRightRadius := "0px", borderBottomRightRadius := "0px"),
    )
  }

}

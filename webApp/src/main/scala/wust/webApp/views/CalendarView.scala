package wust.webApp.views

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
import wust.facades.dateFns.DateFns
import wust.webApp.state.{ FocusState, GlobalState, Placeholder }
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object CalendarView {
  val weekDays = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    // TODO: custom weekstart: Monday/Sunday

    val initialSelectedDate = new js.Date()
    val selectedDate: Var[js.Date] = Var(initialSelectedDate)
    val weeksInMonth: Rx[Int] = Rx { DateFns.getWeeksInMonth(selectedDate()) } // TODO: ,{ weekStartsOn: 1 }
    val daysInMonth: Rx[Int] = Rx { DateFns.getDaysInMonth(selectedDate()) }
    val weekDayOfFirstDayInMonth: Rx[Int] = Rx{ DateFns.getDay(DateFns.setDate(selectedDate(), 1)) }

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
        button(cls := "ui compact basic button", "<", onClickDefault.useLazy(DateFns.subMonths(selectedDate.now, 1)) --> selectedDate),
        button(cls := "ui compact basic button", ">", onClickDefault.useLazy(DateFns.addMonths(selectedDate.now, 1)) --> selectedDate),
        marginBottom := "20px",
      ),
      div(
        marginBottom := "20px",
      ),
      div(
        flexGrow := 1,
        display := "grid",
        style("grid-template-columns") := "repeat(7, 1fr)",
        style("grid-template-rows") <-- Rx { s"20px repeat(${weeksInMonth()}, 1fr)" }, // 1 row for weekdays
        style("grid-column-gap") := "3px",
        style("grid-row-gap") := "3px",

        List.tabulate(7)(i => div(weekDays(i))),
        Rx {
          List.tabulate(weekDayOfFirstDayInMonth())(offset => div(
            //TODO: display days of previous month
            opacity := 0.5,
            padding := "10px",
          // backgroundColor := Colors.contentBgShade,
          // offset,
          ))
        },

        Rx {
          List.tabulate(daysInMonth()){ index =>
            val day = index + 1
            val dateOfCell = DateFns.setDate(selectedDate(), day)
            val isToday = DateFns.isSameDay(dateOfCell, new js.Date())
            div(
              VDomModifier.ifTrue(isToday)(outline := "2px solid #f26b4d"),
              backgroundColor := Colors.contentBgShade,
              div(day, margin := "5px 10px"),

              VDomModifier.ifTrue(day == 7)(event("Here Event")),
              VDomModifier.ifTrue(isToday)(event("Do it Today")),
            )
          }
        }

        //TODO: display days of next month
      )
    )
  }

  def event(content: VDomModifier) = div(
    content,
    backgroundColor := "#00aefd",
    color := "white",
    fontWeight.bold,
    borderRadius := "3px",
    margin := "2px",
    padding := "3px 5px",
  )

}

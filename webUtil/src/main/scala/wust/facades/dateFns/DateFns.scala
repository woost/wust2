package wust.facades.dateFns

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSImport("date-fns", JSImport.Default)
object DateFns extends js.Object {

  // https://date-fns.org/v2.0.0-alpha.16/docs
  def format(date: js.Date, format: String): String = js.native
  def format(date: js.Date, format: String, options: js.Dynamic): String = js.native
  def formatDistance(date: js.Date, baseDate: js.Date): String = js.native
  def addWeeks(date: js.Date, amount: Int): js.Date = js.native
  def differenceInCalendarDays(date: js.Date, createdDate: js.Date): Int = js.native
  def getDay(date: js.Date): Int = js.native
  def getMonth(date: js.Date): Int = js.native
  def getYear(date: js.Date): Int = js.native
  def getWeeksInMonth(date: js.Date): Int = js.native
  def getDaysInMonth(date: js.Date): Int = js.native
  def addMonths(date: js.Date, amount: Int): js.Date = js.native
  def addDays(date: js.Date, amount: Int): js.Date = js.native
  def subMonths(date: js.Date, amount: Int): js.Date = js.native
  def getDate(date: js.Date): Int = js.native
  def setDate(date: js.Date, dayOfMonth: Int): js.Date = js.native
  def setWeek(date: js.Date, week: Int): js.Date = js.native
  def isSameDay(dateLeft: js.Date, dateRight: js.Date): Boolean = js.native
  def eachWeekOfInterval(interval: Interval): js.Array[js.Date] = js.native
}

trait Interval extends js.Object {
  var start: js.Date
  var end: js.Date
}

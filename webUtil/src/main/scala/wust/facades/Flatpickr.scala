package wust.facades.flatpickr

import org.scalajs.dom
import scala.scalajs.js.|
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("flatpickr", JSImport.Default)
object Flatpickr extends js.Object {
  def apply(elem: dom.Element | String, options: FlatpickrOptions = ???): FlatpickrInstance = js.native
}

@js.native
trait FlatpickrInstance extends js.Object {
  def clear(): Unit
  def open(): Unit
  def close(): Unit
  def destroy(): Unit
}

trait FlatpickrOptions extends js.Object {
  var altFormat: js.UndefOr[String] = js.undefined //Exactly the same as date format, but for the altInput field
  var altInput: js.UndefOr[Boolean] = js.undefined //Show the user a readable date (as per altFormat), but return something totally different to the server.
  var altInputClass: js.UndefOr[String] = js.undefined //This class will be added to the input element created by the altInput option.  Note that altInput already inherits classes from the original input.
  var allowInput: js.UndefOr[Boolean] = js.undefined //Allows the user to enter a date directly input the input field. By default, direct entry is disabled.
  var appendTo: js.UndefOr[dom.Element] = js.undefined //Instead of body, appends the calendar to the specified node instead*.
  var ariaDateFormat: js.UndefOr[String] = js.undefined //Defines how the date will be formatted in the aria-label for calendar days, using the same tokens as dateFormat. If you change this, you should choose a value that will make sense if a screen reader reads it out loud.
  var clickOpens: js.UndefOr[Boolean] = js.undefined //Whether clicking on the input should open the picker. You could disable this if you wish to open the calendar manually with.open()
  var dateFormat: js.UndefOr[String] = js.undefined //A string of characters which are used to define how the date will be displayed in the input box. The supported characters are defined in the table below.
  var defaultDate: js.UndefOr[String | js.Date | js.Array[js.Date]] = js.undefined // Sets the initial selected date(s). If you're using mode: "multiple" or a range calendar supply an Array of Date objects or an Array of date strings which follow your dateFormat.Otherwise, you can supply a single Date object or a date string.
  var defaultHour: js.UndefOr[Double] = js.undefined //Initial value of the hour element.
  var defaultMinute: js.UndefOr[Double] = js.undefined // Initial value of the minute element.
  // var disable	Array	[]	See Disabling dates
  var disableMobile: js.UndefOr[Boolean] = js.undefined //Set disableMobile to true to always use the non-native picker.  By default, flatpickr utilizes native datetime widgets unless certain options (e.g. disable) are used.
  // var enable	Array	[]	See Enabling dates
  var enableTime: js.UndefOr[Boolean] = js.undefined //Enables time picker
  var enableSeconds: js.UndefOr[Boolean] = js.undefined //Enables seconds in the time picker.
  // var formatDate	Function	null	Allows using a custom date formatting function instead of the built-in handling for date formats using dateFormat, altFormat, etc.
  // var hourIncrement	Integer	1	Adjusts the step for the hour input (incl. scrolling)
  var inline: js.UndefOr[Boolean] = js.undefined //Displays the calendar inline
  // var maxDate	String/Date	null	The maximum date that a user can pick to (inclusive).
  // var minDate	String/Date	null	The minimum date that a user can start picking from (inclusive).
  // var minuteIncrement	Integer	5	Adjusts the step for the minute input (incl. scrolling)
  var mode: js.UndefOr[String] = js.undefined //"single", "multiple", or "range"
  // var nextArrow: js.UndefOr[String] = >	HTML for the arrow icon, used to switch months.
  var noCalendar: js.UndefOr[Boolean] = js.undefined //Hides the day selection in calendar.  Use it along with enableTime to create a time picker.
  var onChange: js.UndefOr[js.Function3[js.Array[js.Date], String, js.Any, Unit]] = js.undefined //Function(s) to trigger on every date selection. See Events API
  var onClose: js.UndefOr[js.Function3[js.Array[js.Date], String, js.Any, Unit]] = js.undefined //Function(s) to trigger on every time the calendar is closed. See Events API
  var onOpen: js.UndefOr[js.Function3[js.Array[js.Date], String, js.Any, Unit]] = js.undefined //Function(s) to trigger on every time the calendar is opened. See Events API
  var onReady: js.UndefOr[js.Function3[js.Array[js.Date], String, js.Any, Unit]] = js.undefined //Function to trigger when the calendar is ready. See Events API
  // var parseDate	Function	js.undefined //Function that expects a date string and must return a Date object
  var position: js.UndefOr[String] = js.undefined //Where the calendar is rendered relative to the input. "auto", "above" or "below"
  var prevArrow: js.UndefOr[String] = js.undefined //HTML for the left arrow icon.
  var shorthandCurrentMonth: js.UndefOr[Boolean] = js.undefined //Show the month using the shorthand version (ie, Sep instead of September).
  // var showMonths	Integer	1	The number of months showed.
  var static: js.UndefOr[Boolean] = js.undefined //Position the calendar inside the wrapper and next to the input element*.
  var time_24hr: js.UndefOr[Boolean] = js.undefined //Displays time picker in 24 hour mode without AM/PM selection when enabled.
  var weekNumbers: js.UndefOr[Boolean] = js.undefined //Enables display of week numbers in calendar.
  var wrap: js.UndefOr[Boolean] = js.undefined //Custom elements and input groups
}


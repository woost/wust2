package wust.webApp.views

import wust.ids._
import scala.scalajs.js

object DueDate {
  import colorado.RGB

  private def clampDate(now: EpochMilli): EpochMilli = {
    val date = new js.Date(now)
    date.setHours(0)
    date.setMinutes(0)
    date.setSeconds(0)
    date.setMilliseconds(0)
    EpochMilli(date.getTime.toLong)
  }

  final case class Bucket(
    days: Int,
    name: String,
    color: RGB,
    bgColor: RGB
  ) {
    def inBucket(now: EpochMilli, time: EpochMilli): Boolean = time isBefore (clampDate(now) plus daysMilli)
    def daysMilli: DurationMilli = DurationMilli.day times days
    def isFarFuture: Boolean = days >= 7
  }

  object Bucket {
    val values: Array[Bucket] = Array(
      Bucket(days = 0, "Overdue", RGB(117, 0, 14), RGB(254, 221, 224)),
      Bucket(days = 1, "Today", RGB(117, 31, 0), RGB(255, 186, 179)),
      Bucket(days = 2, "Tomorrow", RGB(102, 68, 0), RGB(255, 247, 179)),
      Bucket(days = 7, "Within a Week", RGB(34, 156, 156), RGB(75, 192, 192)),
      Bucket(days = 30, "Within a Month", RGB(29, 116, 143), RGB(54, 162, 235))
    )
  }

  def bucketOf(now: EpochMilli, time: EpochMilli): Option[Bucket] = Bucket.values.find(_.inBucket(now, time))
}

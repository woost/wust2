package wust.webApp

import wust.facades.juration.Juration
import wust.ids._
import wust.util.StringOps

import scala.scalajs.js
import scala.scalajs.js.Date
import scala.util.control.NonFatal

object StringJsOps {
  @inline def safeToDuration(durationString: String): Either[String, DurationMilli] = {
    try {
      Right(DurationMilli(Juration.parse(durationString).toLong * 1000))
    } catch { case NonFatal(e) =>
      val msg = e.getMessage.split(": ").drop(1).mkString(": ") // split of function name of juration in exception, e.g.: "juration.parse(): something..."
      Left(msg)
    }
  }
  @inline def durationToString(duration: DurationMilli): String = {
    Juration.stringify(duration / 1000)
  }

  @inline def dateToString(date: DateMilli): String = {
    new js.Date(date).toLocaleDateString()
  }

  @inline def dateTimeToString(date: DateTimeMilli): String = {
    new js.Date(date).toLocaleString()
  }

  @inline def timeToTimeString(t: TimeMilli): String = {
    val date = new Date(t)
    f"${date.getHours}%02d:${date.getMinutes}%02d"
  }

  def timeStringToTime(str: String): Option[TimeMilli] = {
    //format 24h: hh:mm
    str.split(":") match {
      case Array(hour, min) => for {
        hour <- StringOps.safeToInt(hour)
        if hour >= 0 && hour < 24
        min <- StringOps.safeToInt(min)
        if min >= 0 && min < 60
      } yield TimeMilli(EpochMilli(EpochMilli.hour * hour + EpochMilli.minute * min))
      case _ => None
    }
  }
}

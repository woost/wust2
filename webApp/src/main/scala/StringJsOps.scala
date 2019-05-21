package wust.webApp

import scala.scalajs.js
import wust.ids.{DurationMilli, EpochMilli, TimeMilli}
import juration._
import wust.util.StringOps

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
    juration.Juration.stringify(duration / 1000)
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

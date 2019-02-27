package wust.webApp

import scala.scalajs.js
import wust.ids.{EpochMilli, DurationMilli}
import juration._

import scala.util.control.NonFatal

object StringJsOps {
  @inline def safeToEpoch(epochString: String): Option[EpochMilli] = {
    try { Some(EpochMilli(new js.Date(epochString).getTime.toLong)) } catch { case NonFatal(_) => None }
  }
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
}

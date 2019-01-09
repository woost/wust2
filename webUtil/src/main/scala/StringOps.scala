package wust.webUtil

import scala.scalajs.js
import wust.ids.EpochMilli

object StringOps {
  @inline def toEpoch(epochString: String): EpochMilli = {
    EpochMilli(new js.Date(epochString).getTime.toLong)
  }
  @inline def safeToEpoch(epochString: String): Option[EpochMilli] = {
    try { Some(EpochMilli(new js.Date(epochString).getTime.toLong)) } catch { case _: Throwable => None }
  }
}

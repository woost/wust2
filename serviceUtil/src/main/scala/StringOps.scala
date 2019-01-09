package wust.serviceUtil

import wust.ids.EpochMilli

object StringOps {
  @inline def toEpoch(time: String): EpochMilli = {
    import java.time.Instant
    EpochMilli(Instant.parse(time).toEpochMilli)
  }
  @inline def safeToEpoch(time: String): Option[EpochMilli] = {
    try { Some(toEpoch(time)) } catch { case _: Throwable => None }
  }
}

package wust.api.serialize

import java.time.{ZoneId, Instant, LocalDateTime}

object Helper {
  //TODO: LocalDateTime is internally stored as two Longs, does the precision loss matter?
  def toMillis(ldt: LocalDateTime) = ldt.atZone(ZoneId.systemDefault).toInstant.toEpochMilli
  def fromMillis(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault).toLocalDateTime
}

package wust.ids

sealed trait JoinDate {
  def timestamp: EpochMilli
}

object JoinDate {
  case object Never extends JoinDate {
    // TODO: negative year not working: https://github.com/mauricio/postgresql-async/issues/248
    def timestamp: EpochMilli = EpochMilli(-62135596800000L) // new JodaDateTime(1, 1, 1, 0, 0, 0, 0, JodaDateTimeZone.UTC).getMillis, https://www.postgresql.org/docs/9.1/static/datatype-datetime.html
  }
  case class Until(timestamp: EpochMilli) extends JoinDate
  case object Always extends JoinDate {
    def timestamp:EpochMilli = EpochMilli(9224286393600000L) // new JodaDateTime(294276, 1, 1, 0, 0, 0, 0, JodaDateTimeZone.UTC).getMillis
  }

  val from: EpochMilli => JoinDate =  {
    case timestamp if timestamp >= JoinDate.Always.timestamp => JoinDate.Always
    case timestamp if timestamp <= JoinDate.Never.timestamp => JoinDate.Never
    case timeStamp => JoinDate.Until(timeStamp)
  }
}


sealed trait AccessLevel
object AccessLevel {
  case object Read extends AccessLevel
  case object ReadWrite extends AccessLevel
}






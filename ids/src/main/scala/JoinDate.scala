package wust.ids

sealed trait JoinDate {
  def timestamp: EpochMilli
}

object JoinDate {
  case object Never extends JoinDate {
    // TODO: negative year not working: https://github.com/mauricio/postgresql-async/issues/248
    def timestamp: EpochMilli = EpochMilli.min
  }
  case class Until(timestamp: EpochMilli) extends JoinDate
  case object Always extends JoinDate {
    def timestamp: EpochMilli = EpochMilli.max
  }

  val from: EpochMilli => JoinDate = {
    case timestamp if timestamp >= JoinDate.Always.timestamp => JoinDate.Always
    case timestamp if timestamp <= JoinDate.Never.timestamp  => JoinDate.Never
    case timeStamp                                           => JoinDate.Until(timeStamp)
  }
}

package wust.ids

sealed trait DeletedDate {
  def timestamp: EpochMilli
}

object DeletedDate {
  case class Deleted(timestamp: EpochMilli) extends DeletedDate
  case object NotDeleted extends DeletedDate {
    def timestamp:EpochMilli = EpochMilli(9224286393600000L) // new JodaDateTime(294276, 1, 1, 0, 0, 0, 0, JodaDateTimeZone.UTC).getMillis
  }

  val from: EpochMilli => DeletedDate =  {
    case timestamp if timestamp >= DeletedDate.NotDeleted.timestamp => DeletedDate.NotDeleted
    case timeStamp => DeletedDate.Deleted(timeStamp)
  }
}


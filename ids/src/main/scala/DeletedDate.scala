package wust.ids

sealed trait DeletedDate {
  def timestamp: EpochMilli
}

object DeletedDate {
  case class Deleted(timestamp: EpochMilli) extends DeletedDate
  case object NotDeleted extends DeletedDate {
    def timestamp:EpochMilli = EpochMilli.max
  }

  val from: EpochMilli => DeletedDate =  {
    case timestamp if timestamp >= DeletedDate.NotDeleted.timestamp => DeletedDate.NotDeleted
    case timeStamp => DeletedDate.Deleted(timeStamp)
  }
}


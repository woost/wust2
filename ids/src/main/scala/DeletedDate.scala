package wust.ids

sealed trait DeletedDate {
  def timestamp: EpochMilli
  def isNowDeleted = EpochMilli.now > timestamp
}

object DeletedDate {
  case class Deleted(timestamp: EpochMilli) extends DeletedDate
  object Deleted {
    def now = new Deleted(EpochMilli.now)
  }
  case object NotDeleted extends DeletedDate {
    def timestamp:EpochMilli = EpochMilli.max
  }

  val from: EpochMilli => DeletedDate =  {
    case timestamp if timestamp >= DeletedDate.NotDeleted.timestamp => DeletedDate.NotDeleted
    case timeStamp => DeletedDate.Deleted(timeStamp)
  }
}


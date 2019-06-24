package wust.ids

import wust.util.macros.SubObjects

sealed trait OAuthClientService {
  def identifier: String
}
object OAuthClientService {
  case object Pushed extends OAuthClientService {
    def identifier = "pushed.co"
  }

  def fromString: String => Option[OAuthClientService] = {
    case "pushed.co" => Some(Pushed)
    case _ => None
  }

  def all: Array[OAuthClientService] = SubObjects.all[OAuthClientService]
}

package wust.webApp.state

import acyclic.file
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{NodeId, View}

sealed trait PresentationMode
object PresentationMode {
  case object Full extends PresentationMode
  sealed trait Alternative extends PresentationMode

  case object ContentOnly extends Alternative
  case object Doodle extends Alternative

  def toString(p: PresentationMode): Option[String] = Some(p) collect {
    case ContentOnly => "content"
    case Doodle => "doodle"
  }

  def fromString(s: String): Option[PresentationMode] = Some(s.toLowerCase) collect {
    case "content" => ContentOnly
    case "doodle" => Doodle
    case "full" => Full
  }
}

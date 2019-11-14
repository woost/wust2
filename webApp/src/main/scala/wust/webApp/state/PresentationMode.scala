package wust.webApp.state

import acyclic.file
import wust.api.Authentication
import wust.graph.Page
import wust.webApp.WoostConfig
import wust.ids.{NodeId, View}

sealed trait PresentationMode
object PresentationMode {
  case object Full extends PresentationMode
  sealed trait Alternative extends PresentationMode

  case object ContentOnly extends Alternative
  case object Doodle extends Alternative
  case object ThreadTracker extends Alternative

  val defaultMode = WoostConfig.value.defaultMode.toOption.flatMap(fromString(_)).getOrElse(PresentationMode.Full)

  def toString(p: PresentationMode): Option[String] = Some(p).filter(_ != defaultMode) collect {
    case ContentOnly => "content"
    case Doodle => "doodle"
    case ThreadTracker => "threadtracker"
    case Full => "full"
  }

  def fromString(s: String): Option[PresentationMode] = Some(s.toLowerCase) collect {
    case "content" => ContentOnly
    case "doodle" => Doodle
    case "threadtracker" => ThreadTracker
    case "full" => Full
  }
}

package wust.webApp.state

import acyclic.file
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{NodeId, View}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._

sealed trait PresentationMode
object PresentationMode {
  case object Full extends PresentationMode
  sealed trait Alternative extends PresentationMode

  case object ContentOnly extends Alternative
  case object Doodle extends Alternative
  case object ThreadTracker extends Alternative

  def toString(p: PresentationMode): Option[String] = Some(p) collect {
    case ContentOnly => "content"
    case Doodle => "doodle"
    case ThreadTracker => "threadtracker"
  }

  def fromString(s: String): Option[PresentationMode] = Some(s.toLowerCase) collect {
    case "content" => ContentOnly
    case "doodle" => Doodle
    case "threadtracker" => ThreadTracker
    case "full" => Full
  }


  def showOnlyInFullMode(modifier: => VDomModifier, additionalModes:List[PresentationMode] = Nil)(implicit ctx:Ctx.Owner): VDomModifier = {
    GlobalState.presentationMode.map {
      case PresentationMode.Full => modifier
      case mode if additionalModes contains mode => modifier
      case _ => VDomModifier.empty
    }
  }
}

package views

import com.raquo.domtypes.generic.codecs.BooleanAsAttrPresenceCodec
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.BasicAttrBuilder
import wust.graph.PostContent

import scala.util.matching.Regex

object MediaViewer {
  private val youtubeUrl = (Regex.quote("https://www.youtube.com/watch?v=") + "([^ ]+)").r //TODO better
  def embed(content: PostContent.Link): VNode = content.url match {
    case youtubeUrl(id) =>
      iframe(width := "560", height := "315", src := s"https://www.youtube.com/embed/$id", attr("frameborder") := "0", attr("allow") := "autoplay; encrypted-media", attr[Boolean]("allowfullscreen", b => b) := true)
    case url => a(href := url, url)

  }
}

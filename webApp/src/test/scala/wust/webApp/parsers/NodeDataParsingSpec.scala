package wust.webApp.parsers

import org.scalatest.{FreeSpec, MustMatchers}
import wust.ids.NodeData

class NodeDataParsingSpec extends FreeSpec with MustMatchers {
  "empty String" in {
    val (content, tags) = NodeDataParser.taggedContent.parse("").get.value
    val expected = NodeData.Markdown("")
    content mustEqual expected
    tags mustEqual Seq.empty
  }

  "media" in {
    val url = "http://haio.pai"
    val (content, tags) = NodeDataParser.taggedContent.parse(url).get.value
    val expected = NodeData.Link(url)
    content mustEqual expected
    tags mustEqual Seq.empty
  }

  "content with single tag" in {
    val text = "ich bin einfach"
    val (content, tags) = NodeDataParser.taggedContent.parse(text + "#foo").get.value
    val expected = NodeData.Markdown(text)
    content mustEqual expected
    tags mustEqual Seq("foo")
  }

  "content with multiple tags" in {
    val text = "so ist das nunmal"
    val (content, tags) = NodeDataParser.taggedContent.parse(text + "#foo #bar").get.value
    val expected = NodeData.Markdown(text)
    content mustEqual expected
    tags mustEqual Seq("foo", "bar")
  }

  "content with umlauts" in {
    val text = "so ist äöü nunmal"
    val (content, tags) = NodeDataParser.taggedContent.parse(text + """#äöü #müh!""").get.value
    val expected = NodeData.Markdown(text)
    content mustEqual expected
    tags mustEqual Seq("äöü", "müh!")
  }

  "media with tags" in {
    val url = "http://haio.pai"
    val (content, tags) = NodeDataParser.taggedContent.parse(s"$url #foo").get.value
    val expected = NodeData.Link(url)
    content mustEqual expected
    tags mustEqual Seq("foo")
  }
}

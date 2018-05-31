package wust.webApp.parsers

import org.scalatest.{FreeSpec, MustMatchers}
import wust.ids.NodeData

class NodeDataParsingSpec extends FreeSpec with MustMatchers {
  "empty String" in {
    val (content, tags) = PostDataParser.taggedContent.parse("").get.value
    val expected = NodeData.Markdown("")
    content mustEqual expected
    tags mustEqual Seq.empty
  }

  "media" in {
    val url = "http://haio.pai"
    val (content, tags) = PostDataParser.taggedContent.parse(url).get.value
    val expected = NodeData.Link(url)
    content mustEqual expected
    tags mustEqual Seq.empty
  }

  "content with tags" in {
    val text = "so ist das nunmal"
    val (content, tags) = PostDataParser.taggedContent.parse(text + "#foo #bar").get.value
    val expected = NodeData.Markdown(text)
    content mustEqual expected
    tags mustEqual Seq("foo", "bar")
  }

  "content with space-tags" in {
    val text = "so ist das nunmal"
    val (content, tags) = PostDataParser.taggedContent.parse(text + """#foo #"hans dieter"""").get.value
    val expected = NodeData.Markdown(text)
    content mustEqual expected
    tags mustEqual Seq("foo", "hans dieter")
  }

  "media with tags" in {
    val url = "http://haio.pai"
    val (content, tags) = PostDataParser.taggedContent.parse(s"$url #foo").get.value
    val expected = NodeData.Link(url)
    content mustEqual expected
    tags mustEqual Seq("foo")
  }
}

package frontend

import org.scalajs.dom._
import mhtml._
import scala.xml.Node

import graph._

object ListView {
  def component(graph: Rx[Graph]): Node =
    <p>{graph.map(_.toString)}</p>
}

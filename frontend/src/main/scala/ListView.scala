package frontend

import org.scalajs.dom._

import diode._
import diode.react._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import graph._

object ListView {
  val component = ReactComponentB[ModelProxy[Graph]]("ListView")
    .render_P { proxy =>
      val graph = proxy.value
      <.p(graph.toString)
    }
    .build
}

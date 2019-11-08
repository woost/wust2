package wust.webApp.views

import cats.data.EitherT
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import outwatch.reactive.handler._
import rx._
import wust.css.Styles
import wust.graph._
import wust.api.NodeTemplate
import wust.ids._
import wust.webApp.Client
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.state.GraphChangesAutomation
import wust.webUtil.UI
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import monix.eval.Task

import fontAwesome._
import wust.facades.stripe._
import org.scalajs.dom
import scala.scalajs.js
import scala.util.{Success, Failure}

import scala.concurrent.Future

object AdminView {

  def apply = {
    val templateName = SinkSourceHandler[TemplateName]
    val nodeId = SinkSourceHandler[NodeId]

    val allTemplates = SourceStream.fromFuture(Client.api.getTemplates())

    div(
      padding := "20px",
      Styles.growFull,
      Styles.flex,
      justifyContent.center,

      div(

        h3("Admin-UI"),

        div(
          marginTop := "20px",
          cls := "ui form",

          h4("Templates"),

          allTemplates.map { template =>
            div(
              template.toString //TODO render and reload
            )
          },

          div(
            label("Name"),
            input(
              tpe := "text",
              onChange.value.map(TemplateName(_)) --> templateName
            )
          ),
          div(
            label("NodeId"),
            input(
              tpe := "text",
              onChange.value.mapFilter(str => Cuid.fromBase58String(str).toOption.map(NodeId(_))) --> nodeId
            )
          ),
          button(
            cls := "ui button",
            "Add",
            onClick.stopPropagation
              .useLatest(templateName.combineLatestMap(nodeId)((name, nodeId) => NodeTemplate(name, nodeId)))
              .foreach { template =>
                Client.api.setTemplate(template)
              }
          )
        )
      )
    )

  }
}

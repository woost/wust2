package wust.webApp.views

import cats.data.EitherT
import outwatch._
import outwatch.dsl._
import colibri._
import colibri.ext.rx._
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
    val templateNameString = Subject.replay[String]
    val templateName = templateNameString.map(TemplateName(_))
    val nodeIdString = Subject.replay[String]
    val nodeId = nodeIdString.mapFilter(str => Cuid.fromBase58String(str).toOption.map(NodeId(_)))

    val allTemplates = Observable.fromFuture(Client.api.getTemplates())

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

          h5("Existing Templates"),

          div(
            marginLeft := "10px",
            cls := "enable-text-selection",
            //TODO render and reload after save
            allTemplates.map(_.map { template =>
              div(
                Styles.flex,
                b(template.name, marginRight := "10px"),
                template.nodeId.toBase58
              )
            })
          ),

          h5("New Template"),

          div(
            label("Name"),
            input(
              tpe := "text",
              value <-- templateNameString,
              onChange.value --> templateNameString
            )
          ),
          div(
            label("NodeId"),
            input(
              tpe := "text",
              value <-- nodeIdString,
              onChange.value --> nodeIdString
            )
          ),
          button(
            cls := "ui button",
            "Add",
            onClick.stopPropagation
              .useLatest(templateName.combineLatestMap(nodeId)((name, nodeId) => NodeTemplate(name, nodeId)))
              .foreach { template =>
                Client.api.setTemplate(template).onComplete {
                  case Success(value) =>
                    templateNameString.onNext("")
                    nodeIdString.onNext("")
                    UI.toast("Saved new Template.", level = UI.ToastLevel.Success)
                  case Failure(err) =>
                    UI.toast("Error saving new template.", level = UI.ToastLevel.Warning)
                }
              }
          )
        )
      )
    )

  }
}

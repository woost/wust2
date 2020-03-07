package wust.webApp.views

import cats.data.EitherT
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
import outwatch.reactive.handler._
import rx._
import wust.css.Styles
import wust.graph._
import wust.api.{StripeSessionId, StripeCheckoutResponse}
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

object TemplateView {

  case class TemplateDescription(
    name: TemplateName,
    shortDescription: String,
    longDescription: String,
    imgSrc: String
  )

  def render = {

    val templates: Seq[TemplateDescription] =
      TemplateDescription(
        name = TemplateName("CTC"),
        shortDescription = "Coaching Kanban Board",
        longDescription = "Setup a Kanban Board for Coaching external Entities",
        imgSrc = "templates/ctc.png" //TODO
      ) ::
      TemplateDescription(
        name = TemplateName("HR"),
        shortDescription = "Human-Resource Kanban Board",
        longDescription = "Setup a Kanban Board for managing HR pipelines",
        imgSrc = "templates/hr.png" //TODO
      ) ::
      Nil

    val isRunning = Var(false)

    def setupTemplate(name: TemplateName): Unit = if (!isRunning.now) {
      isRunning() = true
      Client.api.getTemplate(name).onComplete {
        case Success(Some(template)) =>
          Client.api.getGraph(Page(template.nodeId)).onComplete {
            case Success(graph) =>
              graph.idToIdxForeach(template.nodeId) { templateIdx =>
                graph.nodes(templateIdx) match {
                  case templateNode: Node.Content =>
                    val newNode = templateNode.copy(id = NodeId.fresh, meta = NodeMeta.default)
                    val changes = GraphChangesAutomation.copySubGraphOfNode(GlobalState.userId.now, graph, newNode, templateNodeIdxs = Array(templateIdx), isFullCopy = true)
                    GlobalState.submitChanges(changes merge GraphChanges(addNodes = Array(newNode)) merge GraphChanges.connect(Edge.Pinned)(newNode.id, GlobalState.userId.now))
                    GlobalState.urlConfig.update(_.focus(Page(newNode.id), needsGet = false))
                  case _ => ()
                }
              }

              isRunning() = false
            case _ =>
              isRunning() = false
              UI.toast("Cannot get Data for the referenced Template. Please try again later.", level = UI.ToastLevel.Warning)
          }
        case _ =>
          isRunning() = false
          UI.toast("Cannot find the referenced Template. Please try again later.", level = UI.ToastLevel.Warning)
      }
    }

    def templateBlock(template: TemplateDescription) = div(
      margin := "10px",
      div(cls := "ui card",
        // div(cls := "image",
        //   img(src := template.imgSrc, width := "50px", height := "50px")
        // ),
        div(cls := "content",
          a(cls := "header",
            template.name
          ),
          div(cls := "meta",
            template.shortDescription
          ),
          div(cls := "description",
            template.longDescription
          )
        ),
        div(cls := "extra content",
          button(
            "Copy Template",
            cls := "ui button basic",

            disabled <-- isRunning,

            onClick.stopPropagation.foreach {
              setupTemplate(template.name)
            }
          )
        )
      )
    )

    div(

      h3("Templates"),

      div(
        marginTop := "10px",
        Styles.flex,
        flexWrap.wrap,
        alignItems.center,

        templates.map { template =>
          templateBlock(template)
        }
      )
    )
  }
}

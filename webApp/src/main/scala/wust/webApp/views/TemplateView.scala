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
  case class Template(name: String, nodeId: NodeId)

  //TODO get current payment plan of user to visualize what the user currently uses.
  def render = {

    val templates: Seq[TemplateName] =
      TemplateName("CTC") ::
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
                    val changes = GraphChangesAutomation.copySubGraphOfNode(GlobalState.userId.now, GlobalState.rawGraph.now, newNode, templateNodeIdxs = Array(templateIdx), isFullCopy = true)
                    GlobalState.submitChanges(changes merge GraphChanges.connect(Edge.Pinned)(newNode.id, GlobalState.userId.now))
                  case _ => ()
                }
              }

              isRunning() = false
            case _ =>
              isRunning() = false
              UI.toast("Cannot get Data for the referenced Template. Please try again later", level = UI.ToastLevel.Warning)
          }
        case _ =>
          isRunning() = false
          UI.toast("Cannot find the referenced Template. Please try again later", level = UI.ToastLevel.Warning)
      }
    }

    def templateBlock(name: TemplateName) = div(cls := "ui card",
      div(cls := "image",
        freeSolid.faTimes
      ),
      div(cls := "content",
        a(cls := "header",
          "header"
        ),
        div(cls := "meta",
          "meta"
        ),
        div(cls := "description",
          "desc"
        )
      ),
      div(cls := "extra content",
        button(
          "Copy Template",
          cls := "ui button basic",

          disabled <-- isRunning,

          onClick.stopPropagation.foreach {
            setupTemplate(name)
          }
        )
      )
    )

    div(
      padding := "20px",
      Styles.growFull,
      Styles.flex,
      justifyContent.center,

      div(

        h3("Templates"),

        div(
          marginTop := "20px",
          Styles.flex,
          flexWrap.wrap,
          alignItems.flexStart,

          templates.map { template =>
            templateBlock(template),
          }
        )
      )
    )
  }

  val focusButton = {
    div
    // button(
    //   margin := "0 5px",

    //   cls := "ui mini compact button basic",

    //   "Pricing",

    //   onClickDefault.foreach(GlobalState.urlConfig.update(_.focus(View.Payment)))
    // )
  }

  case class PaymentPlanIntent(plan: PaymentPlan, price: Int)
}

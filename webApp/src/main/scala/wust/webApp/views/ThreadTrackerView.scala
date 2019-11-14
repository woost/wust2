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
import wust.api.{ StripeSessionId, StripeCheckoutResponse }
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
import scala.util.{ Success, Failure }

import scala.concurrent.Future
import wust.webUtil.Elements

object ThreadTrackerView extends AppDefinition {

  def landing(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    val chatRoomName = Var("")
    def createRoom(name: String = chatRoomName.now): Unit = {
      val roomName = if(name.nonEmpty) name else "New Chat Room"
      val projectId = NodeId.fresh()
      val newProjectChange = GraphChanges.newProject(
        nodeId = projectId,
        userId = GlobalState.userId.now,
        title = roomName,
        views = Some(List(View.ListWithChat, View.Kanban))
      )
      GlobalState.submitChanges(newProjectChange).foreach { _ =>
        GlobalState.focus(projectId, needsGet = false)
        state.onNext(AppDefinition.State.App)
      }
    }

    div(
      Styles.growFull,
      padding := "20px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,

      div(
        cls := "ui input action",
        input(
          cls := "prompt",
          placeholder := "Room name",
          Elements.valueWithEnter.foreach(createRoom _),
          onChange.value --> chatRoomName
        ),
        button(
          cls := "ui primary button",

          "Create Chat Room",
          onClickDefault.foreach(createRoom())
        ),
      ),
    )
  }

  def app(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    Rx {
      val viewConfig = GlobalState.viewConfig()
      GlobalState.mainFocusState(viewConfig).map(focusState => ListWithChatView(focusState))
    }
  }
}

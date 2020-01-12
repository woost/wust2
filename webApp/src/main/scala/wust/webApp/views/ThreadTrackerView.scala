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
import wust.webApp.{Client, Icons}
import wust.facades.stripe._
import org.scalajs.dom
import scala.scalajs.js
import scala.util.{ Success, Failure }

import scala.concurrent.Future
import wust.webUtil.Elements

object ThreadTrackerView extends AppDefinition {

  def landing(state: Handler[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
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


    def advantageBlock(icon: VDomModifier, title: String, description: String) = div(
      padding := "30px",

      borderRadius := "6px",
      boxShadow := "0 0 10px 0 rgba(128,152,213,0.02), 0 2px 30px 1px rgba(128,152,213,0.1)",
      backgroundColor := "#fff",

      Styles.flex,
      alignItems.center,
      flexDirection.column,
      justifyContent.flexStart,

      div(
        fontSize := "24px",
        icon,
      ),

      h3(title, textAlign.center),

      div(
        description
      )
    )

    div(
      Styles.growFull,
      padding := "20px",
      textAlign.center,
      

      h1("Tired of losing important information in your chat history?"),
      h3("Manage your threads like in an issue tracker."),

      p(b("Threads")," are a way to group chat messages under a specific topic to stay organized.", opacity := 0.5),

      div(
        marginTop := "50px",
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

      div(
        marginTop := "50px",
        display := "grid",
        style("grid-template-columns") := "repeat(3, 300px)",
        style("grid-template-rows") := "250px 350px",
        style("grid-column-gap") := "50px",
        style("grid-row-gap") := "50px",

        advantageBlock(VDomModifier(freeRegular.faCheckCircle, color := "#2196f3"), "Close threads like issues", "When a conversation is finished, close that thread like an issue so you can focus on other relevant content."),
        advantageBlock(VDomModifier(freeRegular.faLifeRing, color := "#009688"), "Create threads retroactively", "Very often you only know afterwards that messages should belong to a thread. That's not a problem, just select the corresponding messages and create a new thread."),
        advantageBlock(VDomModifier(freeSolid.faMagic, color := "#3F51B5"), "Manage threads in a Kanban", "Since threads are the same as issues, they can be managed as cards in a Kanban board. All common features like tagging, due-dates and assignments are included."),
        advantageBlock(VDomModifier(freeRegular.faArrowAltCircleDown, color := "#00bcd4"), "Nest threads as deeply as you need", "Some conversations go deep. Zoom in to stay focused. On each level you have all the features. This way channels are no longer necessary, just do everything with threads. Any messages and sub-threads can easily be embedded into any other thread at different levels."),
        advantageBlock(VDomModifier(freeSolid.faUserLock, color := "#4caf50"), "Private threads", "Have private conversations with specific people or write down your personal tasks. Control permissions precisely for each thread, even for sub-threads."),
        advantageBlock(VDomModifier(freeSolid.faUserPlus, color := "#607D8B"), "Invite external participants to specific threads ", "Instead of inviting externals to your entire workspace, invite them only to content they are supposed to see. When someone shares a thread with you, simply integrate it seamlessly into your own thread management. Invites can be sent out via links, no new account needs to be created to participate."),

        marginBottom := "200px",
      )
    ),
  }

  def app(state: Handler[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    Rx {
      val viewConfig = GlobalState.viewConfig()
      GlobalState.mainFocusState(viewConfig).map(focusState => ListWithChatView(focusState))
    }
  }
}

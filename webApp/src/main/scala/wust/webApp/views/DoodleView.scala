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
import wust.webApp.{Client, Icons}
import wust.webApp.state.{UrlConfig, GlobalState, PresentationMode}
import wust.webApp.parsers.UrlConfigWriter
import cats.data.NonEmptyList
import wust.webApp.views.Components._
import wust.webApp.state.GraphChangesAutomation
import wust.webUtil.UI
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._
import monix.eval.Task

import fontAwesome._
import wust.facades.stripe._
import org.scalajs.dom
import scala.scalajs.js
import scala.util.{Success, Failure}

import scala.concurrent.Future

object DoodleView extends AppDefinition {

  private def createTodoButton(state: SinkObserver[AppDefinition.State]) = button(
    cls := "ui basic primary button",

    "Create Todos!",

    onClick.use(AppDefinition.State.App) --> state
  )

  override def header(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    div(
      paddingLeft := "10px",
      paddingRight := "10px",
      Styles.flex,
      alignItems.center,
      justifyContent.spaceBetween,

      div(
        color := "blue",

        "Woost Todo",

        onClick.use(AppDefinition.State.Landing) --> state,
        cursor.pointer
      ),

      createTodoButton(state).apply(
        cls := "mini compact"
      )
    )
  }

  def landing(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    def advantageBlock(icon: VDomModifier, title: String, description: String) = div(
      margin := "5px",
      padding := "10px",
      height := "200px",
      width := "200px",

      borderRadius := "4px",
      boxShadow := "0px 10px 18px -6px rgba(0,0,0,0.3)",

      Styles.flex,
      alignItems.center,
      flexDirection.column,
      justifyContent.center,

      div(
        padding := "10px",
        icon,
      ),

      b(title),

      div(
        padding := "10px",
        fontSize.small,
        description
      )
    )

    val createButton = createTodoButton(state).apply(
      margin := "10px"
    )

    div(
      Styles.growFull,
      padding := "20px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,

      h3(
        "Get Things Done!"
      ),
      div(
        marginBottom := "20px",
        "With Woost, working together becomes quick and easy."
      ),

      createButton,

      div(
        Styles.flex,
        margin := "20px",

        advantageBlock(VDomModifier(Icons.share, color := "blue"), "Suggest Todos", "Setup and customize your Todos and Requirements."),

        advantageBlock(VDomModifier(Icons.share, color := "gray"), "Invite Participants", "Send the link and work together with other participants live."),

        advantageBlock(VDomModifier(freeSolid.faCheck, color := "green"), "Get Things Done", "Get a quick Overview of what is done and what still needs to be worked on.")
      ),

      createButton
    )
  }

  def app(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {

    div(
      cls := "ui segments",
      div(
        cls := "ui segment",
        padding := "0.5em 0.5em",
        div(
          Styles.flex,
          justifyContent.center,
          doodleForm
        )
      )
    )
  }

  def doodleForm(implicit ctx: Ctx.Owner) = {

    val title = Var[String]("")
    val note = Var[String]("")
    val view = Var[Option[View.Visible]](None)

    def createNode(newNodeId: NodeId) = {
      val addNodes = Array.newBuilder[Node]
      val addEdges = Array.newBuilder[Edge]
      val node = Node.Content(newNodeId, NodeData.Markdown(title.now), NodeRole.Project, NodeMeta(NodeAccess.ReadWrite), views = Some(view.now.toList ++ List(View.Chat)))

      val notes = if (note.now.nonEmpty) {
        val noteNode = Node.Content(NodeId.fresh, NodeData.Markdown(note.now), NodeRole.Note, NodeMeta(NodeAccess.Inherited))

        addNodes += noteNode
        addEdges += Edge.Child(ParentId(node.id), ChildId(noteNode.id))
      }

      addNodes += node
      addEdges += Edge.Pinned(node.id, GlobalState.userId.now)

      GraphChanges(
        addNodes = addNodes.result,
        addEdges = addEdges.result
      )
    }

    def url(node: Node) = {
      val base = UrlConfig.default.focus(Page(node.id))
      val config = node.views.fold(base) {
        case Nil          => base
        case _ :: Nil     => base
        case head :: tail => base.focus(View.Tiled(ViewOperator.Row, NonEmptyList(head, tail)))
      }
      def urlConfigToUrl(urlConfig: UrlConfig) = s"${dom.window.location.origin}${UrlConfigWriter.toString(urlConfig)}"
      urlConfigToUrl(config.copy(mode = PresentationMode.ContentOnly))
    }

    val menu = StepMenu.render(Array(
      StepMenu.Step(
        "What's the occasion?",
        div(
          cls := "ui mini form",
          label("Title"),
          input(
            tpe := "text",
            placeholder := "Enter title",
            value <-- title,
            onInput.value --> title
          ),
          label("Note", marginTop := "10px"),
          div(
            color.gray,
            style("font-variant") := "small-caps",
            "Optional",
            float.right
          ),
          input(
            tpe := "text",
            placeholder := "Add note",
            value <-- note,
            onInput.value --> note,
          )
        ),
        title.map(_.nonEmpty)
      ),
      StepMenu.Step(
        "What do you want to work with?",
        div(
          Components.horizontalMenu(
            Components.MenuItem(
              title = Elements.icon(Icons.tasks),
              description = VDomModifier(
                fontSize.xSmall,
                "Todo-List"
              ),
              active = view.map(_ contains View.List),
              clickAction = { () =>
                view() = Some(View.List)
              }
            ) ::
            Components.MenuItem(
              title = Elements.icon(Icons.kanban),
              description = VDomModifier(
                fontSize.xSmall,
                "Kanban-Board"
              ),
              active = view.map(_ contains View.Kanban),
              clickAction = { () =>
                view() = Some(View.Kanban)
              }
            ) ::
            Components.MenuItem(
              title = Elements.icon(Icons.table),
              description = VDomModifier(
                fontSize.xSmall,
                "Table"
              ),
              active = view.map(_ contains View.Table(NodeRole.Task :: Nil)),
              clickAction = { () =>
                view() = Some(View.Table(NodeRole.Task :: Nil))
              }
            ) ::
            Nil,
            itemWidth = "100px",
          )
        ),
        view.map(_.isDefined)
      ),
      // StepMenu.Step(
      //   "Collaboration Settings",
      //   div(
      //     3
      //   ),
      // ),
      // StepMenu.Step(
      //   "Tell your participants who you are",
      //   div(
      //     4
      //   )
      // )
    )).foreach {
      val nodeId = NodeId.fresh
      GlobalState.submitChanges(createNode(nodeId))
      GlobalState.urlConfig.update(_.focus(Page(nodeId), needsGet = false))
      ()
    }

    div(
      width := "600px",
      height := "300px",

      menu
    )
  }
}

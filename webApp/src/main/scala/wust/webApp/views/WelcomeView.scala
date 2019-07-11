package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.facades.googleanalytics.Analytics
import wust.ids.View
import wust.util._
import wust.webApp.state.{ GlobalState, ScreenSize }
import wust.webApp.views.Components._
import wust.webApp.views.SharedViewElements._
import wust.webUtil.outwatchHelpers._
import wust.graph.GraphChanges
import wust.ids._

object WelcomeView {

  def apply(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      flexDirection.column,
      div(
        padding := "10px",
        height := "100%",
        Styles.flex,
        justifyContent.spaceAround,
        overflow.auto,
        div(
          Rx{
            val user = GlobalState.user().toNode
            VDomModifier(
              h1(
                "Hello ",
                Avatar(user)(width := "1em", height := "1em", cls := "avatar", marginLeft := "0.2em", marginRight := "0.1em", marginBottom := "-3px"),
                displayUserName(user.data),
                "!"
              ),
              div(
                cls := "ui segment",
                maxWidth := "80ex",
                marginBottom := "50px",
                h3("Welcome to Woost!"),
                p("If you are new to Woost, start by creating a Project."),
                p("In a ", b("Project"), " you can invite other people to collaborate. You can also add different tools, like a ", b("Checklist"), ", a ", b("Kanban Board"), " or a ", b("Chat."))
              )
            )
          },
          marginBottom := "10%",
          textAlign.center,
          newProjectButton().apply(
            cls := "primary",
            padding := "20px",
            margin := "0px 40px",
            onClick foreach {
              Analytics.sendEvent("view:welcome", "newproject")
            },
          ),
          Rx{
            val user = GlobalState.user().toNode
            user.data.isImplicit.ifTrue[VDomModifier](
              div(
                cls := "ui segment",
                maxWidth := "80ex",
                marginTop := "50px",
                marginBottom := "50px",
                p("You can use Woost without registration."), p("Everything you create is private (unless you share it). Whenever you want to access your data from another device, just ", a(href := "#", "create an account",
                  onClick.preventDefault(GlobalState.urlConfig.now.focusWithRedirect(View.Signup)) --> GlobalState.urlConfig,
                  onClick.preventDefault foreach { Analytics.sendEvent("topbar", "signup") }), ".")
              )
            )
          },
          div(
            cls := "ui segment",
            maxWidth := "80ex",
            marginTop := "50px",
            marginBottom := "50px",
            h3("Find out what's so great about Woost"),
            p(
              "You can explore Woost yourself, be guided by a tutorial.",
            ),
            button(cls := "ui primary button", "Create example content to play with",
              onClick.stopPropagation.foreach { _ =>
                var changes = GraphChanges.empty
                def addGraphChange(newChanges: GraphChanges): Unit = {
                  changes = changes merge newChanges
                }

                sealed trait Node {
                  def name: String
                  val children: List[Node]
                  val views: Option[List[View.Visible]]
                }
                case class Project(name: String, views: Option[List[View.Visible]] = None, children: List[Node] = Nil) extends Node
                case class Task(name: String, views: Option[List[View.Visible]] = None, children: List[Node] = Nil) extends Node

                def addChange(node: Node, parentId: Option[NodeId] = None): NodeId = {
                  println(s"adding $node, parent: $parentId")
                  val nodeId = NodeId.fresh()
                  node match {
                    case Project(name, views, _) => addGraphChange(GraphChanges.newProject(nodeId, GlobalState.userId.now, name, views = views))
                    case Task(name, views, _)    => 
                      println(GraphChanges.addNodeWithParent(wust.graph.Node.MarkdownTask(name), parentId.map(ParentId(_))))
                      addGraphChange(GraphChanges.addNodeWithParent(wust.graph.Node.MarkdownTask(name), parentId.map(ParentId(_))))
                  }
                  node.children.reverse.foreach(node => addChange(node, Some(ParentId(nodeId))))
                  nodeId
                }

                val checklistId = addChange(
                  Project(
                    "Click me to rename",
                    views = Some(List(View.List)),
                    children = List(
                      Task("check me"),
                      Task("drag me to change order"),
                      Task(
                        "click the + on the right or the progress-bar to expand this task",
                        children = List(
                          Task("check this subtask"),
                        )
                      ),
                    )
                  )
                )

                // val newProjectId = NodeId.fresh()
                // addChange(GraphChanges.newProject(newProjectId, GlobalState.userId.now, "Click me to rename", views = Some(List(View.List))))
                // // checklist items are added in reverse order

                // val expanded = GraphChanges.addMarkdownTask("click the + on the right or the progress-bar to expand this task", ParentId(newProjectId))
                // val expandedId = expanded.addNodes.head.id
                // addChange(expanded)
                // addChange(GraphChanges.addMarkdownTask("create a new sub-subtask (expand a subtask first). Tasks can be nested as you want.", ParentId(expandedId)))
                // addChange(GraphChanges.addMarkdownTask("create a new subtask", ParentId(expandedId)))
                // addChange(GraphChanges.addMarkdownTask("check this subtask", ParentId(expandedId)))

                // addChange(GraphChanges.addMarkdownTask("drag me to change order", ParentId(newProjectId)))
                // addChange(GraphChanges.addMarkdownTask("check me", ParentId(newProjectId)))

                GlobalState.submitChanges(changes)
                GlobalState.focus(checklistId, needsGet = false)
              })
          )
        )
      ),
      Rx {
        (GlobalState.screenSize() == ScreenSize.Small).ifTrue[VDomModifier](
          div(
            padding := "15px",
            div(
              Styles.flex,
              alignItems.center,
              justifyContent.spaceAround,
              AuthControls.authStatus(buttonStyleLoggedIn = "basic", buttonStyleLoggedOut = "primary")
            )
          )
        )
      }
    )
  }
}

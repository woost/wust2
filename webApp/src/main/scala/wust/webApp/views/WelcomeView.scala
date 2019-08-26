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
import wust.webApp.state.FeatureState
import wust.ids.Feature
import wust.graph.Node

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
          Styles.flex,
          flexDirection.column,
          alignItems.center,
          Rx{ welcomeTitle(GlobalState.user().toNode).append(Styles.flexStatic) },
          welcomeMessage(Styles.flexStatic, marginBottom := "50px"),
          newProjectButton(Styles.flexStatic),
          Rx {
            (GlobalState.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](
              tutorialMessage(
                Styles.flexStatic,
                marginTop := "50px",
                marginBottom := "50px",
              )
            )
          },
          div (width := "1px", height := "1px", Styles.flexStatic), // margin bottom hack for flexbox
        )
      ),
      Rx {
        (GlobalState.screenSize() == ScreenSize.Small).ifTrue[VDomModifier](
          authControls
        )
      }
    )
  }

  def newProjectButton = NewProjectPrompt.newProjectButton().apply(
    cls := "primary",
    padding := "20px",
    id := "tutorial-newprojectbutton",
    onClick.stopPropagation.foreach {
      FeatureState.use(Feature.CreateProjectFromWelcomeView)
      MainTutorial.waitForNextStep()
    }
  )

  def welcomeTitle(user: Node.User) = h1(
    "Hello ",
    Avatar(user)(width := "1em", height := "1em", cls := "avatar", marginLeft := "0.2em", marginRight := "0.1em", marginBottom := "-3px"),
    displayUserName(user.data),
    "!"
  )

  def welcomeMessage = div(
    cls := "ui segment",
    maxWidth := "80ex",
    h3("Welcome to Woost."),
    p("If you are new to Woost, start by creating a Project."),
    p("In a ", b("Project"), " you can invite other people to collaborate. You can also add different tools, like a ", b("Checklist"), ", a ", b("Kanban Board"), " or a ", b("Chat."))
  )

  def tutorialMessage = div(
    cls := "ui segment",
    maxWidth := "80ex",
    p(
      "We're different from other collaboration tools in some really special ways. So we'd like to take you through them."
    ),
    div(
      Styles.flex,
      justifyContent.center,
      button(
        cls := "ui pink basic button", "Show me the basics",
        onClick.stopPropagation.foreach {
          MainTutorial.startTour()
        },
        marginBottom := "20px"
      )
    ),
    p(
      "If you want to explore yourself, take a look at ", b("Explored Features"), " in the left sidebar. It will track your progress and suggest things you should try next."
    ),
  )

  def authControls(implicit ctx: Ctx.Owner) =
    div(
      padding := "15px",
      div(
        Styles.flex,
        alignItems.center,
        justifyContent.spaceAround,
        AuthControls.authStatusOnLightBackground
      )
    )
}

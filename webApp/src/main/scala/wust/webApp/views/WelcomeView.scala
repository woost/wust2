package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph.Node
import wust.ids.Feature
import wust.util._
import wust.webApp.state.{FeatureState, GlobalState, ScreenSize, PresentationMode}
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._

object WelcomeView {
  def apply(implicit ctx: Ctx.Owner): VNode = {
    div(
      GlobalState.presentationMode.map { mode =>
        AppDefinition.fromMode(mode).fold(standard)(WoostAppView.render(_))
      }
    )
  }

  def standard(implicit ctx: Ctx.Owner) = VDomModifier(
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
        Rx {
          (GlobalState.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](
            welcomeMessage(Styles.flexStatic, marginBottom := "50px"),
          )
        },
        // TemplateView.render.apply(margin := "10px"),
        newProjectButton(Styles.flexStatic),
        div (width := "1px", height := "1px", Styles.flexStatic), // margin bottom hack for flexbox
      )
    ),

    Rx {
      VDomModifier.ifTrue(GlobalState.screenSize() == ScreenSize.Small)(
        div(
          Styles.flex,
          justifyContent.center,
          PaymentView.focusButton
        ),
        authControls
      )
    }
  )

  def newProjectButton = NewProjectPrompt.newProjectButton().apply(
    cls := "primary",
    padding := "20px",
    id := "tutorial-newprojectbutton",
    onClick.stopPropagation.foreach {
      FeatureState.use(Feature.CreateProjectFromWelcomeView)
    }
  )

  def welcomeTitle(user: Node.User) = h1(
    "Hello ",
    Avatar.user(user, size = "1em")(marginLeft := "0.2em", marginRight := "0.1em", marginBottom := "-3px"),
    span(displayUserName(user.data), cls := "username"),
    "!"
  )

  def welcomeMessage = div(
    textAlign.center,
    cls := "ui segment",
    maxWidth := "60ex",
    h3(replaceEmoji("Welcome to Woost. :wave:")),
    p(
      replaceEmoji("We're different from other collaboration tools in some really special ways. We'd like to take you through them. :rocket:")
    ),
    div(
      Styles.flex,
      alignItems.center,
      flexDirection.column,
      button(
        cls := "welcome-tutorial-button",
        marginTop := "20px",
        cls := "ui pink basic button", replaceEmoji(" Show me the basics"),
        onClick.stopPropagation.foreach {
          MainTutorial.startTour()
        },
      ),
      div(fontSize := "10px", color.gray, "you can re-start the tutorial anytime"),
      marginBottom := "40px",
    ),
    p(
      "If you want to explore the possibilities yourself, take a look at ", b("Explored Features"), replaceEmoji(" in the left sidebar. It will track your progress and suggest things you can try next. :bulb:")
    ),
  )

  def authControls(implicit ctx: Ctx.Owner) =
    div(
      padding := "15px",
      div(
        Styles.flex,
        alignItems.center,
        justifyContent.spaceAround,
        AuthControls.authStatusOnLightBackground(showLogin = true)
      )
    )
}

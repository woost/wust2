package wust.webApp.views

import acyclic.file
import wust.facades.crisp._
import wust.facades.googleanalytics.GoogleAnalytics
import wust.webApp.{ DeployedOnly}
import scala.scalajs.js
import scala.util.Try
import wust.webApp.{ DevOnly, DebugOnly }
import fontAwesome._
import monix.eval.Task
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.css.{ Styles, ZIndex }
import wust.ids.Feature
import wust.sdk.Colors
import wust.webApp.state.{ FeatureDetails, FeatureState, GlobalState, ScreenSize }
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._
import wust.webUtil.UI

object FeatureExplorer {
  //TODO: rating for completed features: "I liked it", "too complicated", "not useful"
  def apply(extraMods: VDomModifier*)(implicit ctx: Ctx.Owner): VDomModifier = {
    val showPopup = Var(true)

    val stats = div(
      div(
        textAlign.center,
        span(
          fontSize := "80px",
          lineHeight := "50px",
          progress
        ),
        span (
          "%",
          fontSize := "40px"
        ),
        scoreBadge(
          fontSize := "20px",
          marginLeft := "15px",
          padding := "6px",
          Rx{ score() }
        ),
      ),
      div(
        textAlign.center,
        "Explored Features",
      ),
    )

    def helpButton(feature: Feature) = span(
      freeSolid.faQuestionCircle,
      cursor.pointer,
      color := "#60758a",
      UI.popup("bottom right") := ("Is this feature unclear?"),
      onClick.stopPropagation.foreach { _ =>
        Try{
          DeployedOnly { FeedbackForm.initCrisp }
          crisp.push(js.Array("do", "chat:show"))
          crisp.push(js.Array("do", "chat:open"))
        }
        GoogleAnalytics.sendEvent("unclear-feature", feature.toString)
      }
    )

    // TODO: show category labels next to suggestions and recents
    val tryNextList = div(
      Rx{
        VDomModifier.ifTrue(FeatureState.next().nonEmpty)(
          div("Things to try next:"),
          FeatureState.next().filter(FeatureDetails.hasDetails).map { feature =>
            val details = FeatureDetails(feature)
            val showDescription = Var(false)
            div(
              div(
                Rx { (if (showDescription()) freeSolid.faCaretDown: VNode else freeSolid.faCaretRight: VNode).apply(marginRight := "0.5em") },
                details.title, fontWeight.bold, fontSize := "1em",
              ),
              onMouseDown.stopPropagation.discard, // prevent rightsidebar from closing
              onClick.stopPropagation.foreach { showDescription() = !showDescription.now },
              cursor.pointer,
              Rx{
                VDomModifier.ifTrue(showDescription())(
                  div(
                    details.description,
                    helpButton(feature)(paddingLeft := "0.5em", float.right),
                    div(clear.both)
                  )
                )
              },
              backgroundColor := "#dbf5ff",
              padding := "5px",
              marginBottom := "3px",
              borderRadius := "4px",
              Styles.wordWrap,
            )
          }
        )
      }
    )

    val recentFirstTimeList = div(
      "Recent:",
      Rx{
        recentFeatures().map { feature =>
          val details = FeatureDetails(feature)
          div(
            div(
              scoreBadge("+1", float.right, marginLeft := "0.5em"),
              span(
                details.title,
                opacity := 0.8,
                Styles.wordWrap,
                fontWeight.bold,
              ),
            ),
            padding := "5px",
          )
        }
      }
    )

    def recentList = div(
      "Recent:",
      Rx{
        FeatureState.recentlyUsed().map { feature =>
          val details = FeatureDetails(feature)
          div(
            Styles.flex,
            verticalAlign.middle,
            alignItems.flexStart,
            div(
              details.title,
              fontSize := "16px",
              fontWeight.bold,
              opacity := 0.8,
              marginRight := "10px",
            ),
            padding := "8px",
            marginBottom := "3px",
          )
        }
      }
    )

    div(
      keyed,
      cls := "feature-explorer",
      stats(marginTop := "5px"),
      tryNextList(marginTop := "30px"),
      DebugOnly(Rx{ recentList(marginTop := "30px") }),
      Rx{
        VDomModifier.ifTrue(recentFeatures().nonEmpty)(
          recentFirstTimeList(marginTop := "20px")
        )
      },

      onClick.stopPropagation.discard, // prevents closing feedback form by global click
      extraMods
    )
  }

  val score = Rx { (FeatureState.firstTimeUsed() -- Feature.secrets).size }
  val recentFeatures = Rx {
    FeatureState.recentFirstTimeUsed().filter(f => Feature.allWithoutSecretsSet.contains(f) && FeatureDetails.hasDetails(f)).take(5)
  }


  val progress: Rx[String] = Rx {
    val total = Feature.allWithoutSecrets.length
    val ratio = Math.ceil(score().toDouble / total.toDouble * 100).min(100.0) // everything greater 0 is at least 1%
    f"${ratio}%0.0f"
  }

  val progressBar = div(
    Rx{ VDomModifier.ifTrue(recentFeatures().isEmpty)(visibility.hidden) },
    backgroundColor := "rgba(95, 186, 125, 0.2)",
    div(
      width <-- progress.map(p => s"$p%"),
      transition := "width 1s",
      backgroundColor := "#5FBA7D",
      height := "4px"
    )
  )

  val scoreBadge = span(
    color.white,
    backgroundColor := "#5FBA7D",
    borderRadius := "4px",
    padding := "2px 5px",
    display.inlineBlock,
  )

  val usedFeatureAnimation = {
    import outwatch.dom.dsl.styles.extra._
    import scala.concurrent.duration._

    val shake = 0.2

    val animationObservable = SourceStream.concatAsync(
      Task(transform := "rotate(-20deg)").delayExecution(shake seconds),
      Task(transform := "rotate(0deg)").delayExecution(shake seconds),
      Task(visibility.hidden).delayExecution(5 seconds)
    ).prepend(transform := "rotate(20deg)")

    div(
      scoreBadge("+1"),
      transition := s"visibility 0s, transform ${shake}s",
      transform := "rotate(0deg)",
      FeatureState.usedNewFeatureTrigger.switchMap(_ => animationObservable).prepend(visibility.hidden)
    )
  }

  val toggleButton = {
    div(
      id := "tutorial-feature-explorer",
      display.inlineBlock, // needed for absolute positioning of usedFeatureAnimation
      span(
        "Explored Features: ",
        b(progress, "% "),
      ),
      progressBar,

      position.relative,
      paddingRight := "30px", // space for the +1 badge
      usedFeatureAnimation(
        position.absolute,
        top := "5px",
        right := "0",
      )
    )
  }

}

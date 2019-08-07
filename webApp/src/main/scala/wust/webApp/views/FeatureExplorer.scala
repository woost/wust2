package wust.webApp.views

import acyclic.file
import wust.webApp.{DevOnly, DebugOnly}
import fontAwesome._
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.ids.Feature
import wust.sdk.Colors
import wust.webApp.state.{FeatureDetails, FeatureState, GlobalState, ScreenSize}
import wust.webUtil.Elements
import wust.webUtil.outwatchHelpers._

object FeatureExplorer {
  //TODO: next to suggested action, a button: "I don't know what to do / what this means" --> track in analytics, open support chat
  //TODO: rating for completed features: "I liked it", "too complicated", "not useful"
  def apply(implicit ctx: Ctx.Owner) = {
    val showPopup = Var(false)
    val activeDisplay = Rx { display := (if (showPopup()) "block" else "none") }

    val progress: Rx[String] = Rx {
      val total = Feature.all.length
      val used = FeatureState.firstTimeUsed().size
      val ratio = used.toDouble / total.toDouble
      f"${ratio * 100}%0.0f"
    }

    val scoreBadge = span(
      color.white,
      backgroundColor := "#5FBA7D",
      borderRadius := "4px",
      padding := "2px 5px",
      marginLeft.auto,
      display.inlineBlock,
    )

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
          Rx{ FeatureState.firstTimeUsed().size }
        ),
      ),
      div(
        textAlign.center,
        "Explored Features",
      ),
    )

    val tryNextList = div(
      "Things to try next:",
      Rx{
        FeatureState.next().take(3).map { feature =>
          val details = FeatureDetails(feature)
          div(
            div(details.title, fontWeight.bold, fontSize := "1.3em"),
            div(details.description),
            backgroundColor := "#c7f0ff",
            padding := "8px",
            marginBottom := "3px",
            borderRadius := "4px",
          )
        }
      }
    )

    val recentFirstTimeList = div(
      "Recent:",
      Rx{
        FeatureState.recentFirstTimeUsed().take(5).map { feature =>
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
            div(
              freeSolid.faCheck,
              marginRight := "5px",
              Styles.flexStatic,
            // color := "#5FBA7D",
            ),
            scoreBadge(
              "+1",
              Styles.flexStatic,
              marginLeft.auto,
            ),
            padding := "8px",
            marginBottom := "3px",
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

    val usedFeatureAnimation = {
      import outwatch.dom.dsl.styles.extra._

      import scala.concurrent.duration._
      val shake = 0.2
      div(
        scoreBadge("+1"),
        // visibility.hidden,
        transition := s"visibility 0s, transform ${shake}s",
        transform := "rotate(0deg)",
        Observable(visibility.hidden) ++ FeatureState.usedNewFeatureTrigger.switchMap{ _ =>
          Observable(visibility.visible) ++
            Observable(transform := "rotate(20deg)") ++
            Observable(transform := "rotate(-20deg)").delayExecution(shake seconds) ++
            Observable(transform := "rotate(0deg)").delayExecution(shake seconds) ++
            Observable(visibility.hidden).delayExecution(5 seconds)
        }
      )
    }

    val progressBar = div(
      Rx{VDomModifier.ifTrue(FeatureState.firstTimeUsed().isEmpty)(visibility.hidden)},
      backgroundColor := "rgba(255,255,255,0.2)",
      div(
        width <-- progress.map(p => s"$p%"),
        transition := "width 1s",
        backgroundColor := "white",
        height := "2px"
      )
    )

    val toggleButton = {
      div(
        div(
          div(
            "Explored Features: ",
            b(progress, "% "),
            freeSolid.faCaretDown
          ),
          div(
            "Next: ",
            Rx{ FeatureState.next().headOption.map(f => FeatureDetails(f).title) },
            position.absolute,
            top := "29px",
            fontSize := "10px",
            lineHeight := "10px",
            opacity := 0.6,
          ),
        ),
        progressBar,
        // like semantic-ui tiny button
        fontSize := "0.85714286rem",
        fontWeight := 700,
        padding := ".58928571em 1.125em .58928571em",
        cursor.pointer,

        onClick.stopPropagation foreach {
          showPopup.update(!_)
        },
        Elements.onGlobalEscape(false) --> showPopup,
        Elements.onGlobalClick(false) --> showPopup,

        position.relative,
        paddingRight := "30px",
        usedFeatureAnimation(
          position.absolute,
          top := "5px",
          right := "0",
        )
      )
    }

    val closeButton = div(
      height := "30px",
      Styles.flex,
      justifyContent.flexEnd,
      alignItems.center,
      Elements.closeButton(
        onClick.stopPropagation(false) --> showPopup
      )
    )

    div(
      toggleButton,
      div(
        activeDisplay,
        width := "280px",
        position.fixed, top := "35px", right <-- Rx{ if (GlobalState.screenSize() == ScreenSize.Small) "0px" else "200px" },
        zIndex := ZIndex.formOverlay,
        padding := "10px",
        borderRadius := "5px",
        cls := "shadow",
        backgroundColor := Colors.sidebarBg,
        color := "#333",

        closeButton(marginRight := "-10px", marginTop := "-5px"),
        stats(marginTop := "5px"),
        tryNextList(marginTop := "30px"),
        DebugOnly(Rx{recentList(marginTop := "30px")}),
        Rx{
          VDomModifier.ifTrue(FeatureState.recentFirstTimeUsed().nonEmpty)(
            recentFirstTimeList(marginTop := "30px")
          )
        },

        onClick.stopPropagation.discard, // prevents closing feedback form by global click
        maxHeight := "calc(100% - 45px)",
        overflowY.auto,
      ),
    )
  }

}

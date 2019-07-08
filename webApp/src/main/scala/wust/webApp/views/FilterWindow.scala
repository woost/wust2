package wust.webApp.views

import outwatch.dom.{VDomModifier, _}
import outwatch.dom.dsl._
import rx.{Ctx, Rx}
import wust.facades.googleanalytics.Analytics
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, Ownable}


object FilterWindow {
  def movableWindow(position: MovableElement.Position)(implicit ctx: Ctx.Owner): MovableElement.Window = {

    MovableElement.Window(
      title = VDomModifier(
        Icons.filter,
        span(marginLeft := "5px", "Filter"),
      ),
      toggleLabel = VDomModifier(
        Icons.filter,
        span(marginLeft := "5px", "Filter"),
        border := "2px solid transparent",
        borderRadius := "3px",
        padding := "2px",
        GlobalState.isAnyFilterActive.map {
          case true =>
            Rx{VDomModifier(
              border := "2px solid rgb(255,255,255)",
              color.white,
            )}:VDomModifier
          case false => VDomModifier.empty
        }
      ),
      isVisible = GlobalState.showFilterList,
      initialPosition = position,
      initialWidth = 260,
      initialHeight = 250,
      resizable = false,
      titleModifier = Ownable(implicit ctx =>
        Rx{VDomModifier(
          backgroundColor := GlobalState.pageStyle().pageBgColor,
          color.white,
        )}
      ),

      bodyModifier = Ownable { implicit ctx =>
        VDomModifier(
          padding := "5px",

          Components.verticalMenu(
            ViewGraphTransformation.allTransformations.map { transformation =>
              Components.MenuItem(
                title = VDomModifier(transformation.icon, marginLeft := "5px", marginRight := "5px"),
                description = transformation.description,
                active = GlobalState.graphTransformations.map(_.contains(transformation.transform) ^ transformation.invertedSwitch),
                clickAction = { () =>
                  GlobalState.graphTransformations.update { transformations =>
                    if (transformations.contains(transformation.transform)) transformations.filter(_ != transformation.transform)
                    else transformations.filterNot(transformation.disablesTransform.contains) ++ (transformation.enablesTransform :+ transformation.transform)
                  }
                  Analytics.sendEvent("filter", transformation.toString)
                }
              )
            }
          ),
          div(
            cursor.pointer,
            Elements.icon(Icons.noFilter),
            span("Reset ALL filters"),
            onClick(GlobalState.defaultTransformations) --> GlobalState.graphTransformations,
            onClick foreach { Analytics.sendEvent("filter", "reset") },
          )
        )
      }
    )
  }
}

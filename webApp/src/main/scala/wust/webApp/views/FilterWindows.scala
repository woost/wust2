package wust.webApp.views

import outwatch.dom.{VDomModifier, _}
import outwatch.dom.dsl._
import rx.{Ctx, Rx}
import wust.facades.googleanalytics.Analytics
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.views.ViewFilter.allTransformations
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, Ownable}


object FilterWindows {
  def moveableWindow(state: GlobalState, position: MovableElement.Position)(implicit ctx: Ctx.Owner): MovableElement.Window = {

    val filterTransformations: Seq[ViewGraphTransformation] = allTransformations(state)

    MovableElement.Window(
      VDomModifier(
        Icons.filter,
        span(marginLeft := "5px", "Filter"),
        state.isFilterActive.map {
          case true =>
            Rx{VDomModifier(
              backgroundColor := state.pageStyle().pageBgColor,
              color.white,
            )}:VDomModifier
          case false => VDomModifier.empty
        }
      ),
      toggle = state.showFilterList,
      initialPosition = position,
      initialWidth = 260,
      initialHeight = 250,
      resizable = false,
      titleModifier = Ownable(implicit ctx =>
        Rx{VDomModifier(
          backgroundColor := state.pageStyle().pageBgColor,
          color.white,
        )}
      ),
      bodyModifier = Ownable { implicit ctx =>
        VDomModifier(
          padding := "5px",

          Components.verticalMenu(
            filterTransformations.map { transformation =>
              Components.MenuItem(
                title = transformation.icon,
                description = transformation.description,
                active = state.graphTransformations.map(_.contains(transformation.transform) ^ transformation.invertedSwitch),
                clickAction = { () =>
                  state.graphTransformations.update { transformations =>
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
            onClick(state.defaultTransformations) --> state.graphTransformations,
            onClick foreach { Analytics.sendEvent("filter", "reset") },
          )
        )
      }
    )
  }
}

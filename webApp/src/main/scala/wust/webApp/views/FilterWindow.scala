package wust.webApp.views

import outwatch.dom.dsl._
import outwatch.dom.{VDomModifier, _}
import rx.{Ctx, Rx}
import wust.css.Styles
import wust.ids.Feature
import wust.webApp.Icons
import wust.webApp.state.{FeatureState, GlobalState}
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, Ownable}

object FilterWindow {
  val resetAllFiltersText = "Reset all filters"
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
            Rx{
              VDomModifier(
                border := "2px solid rgb(255,255,255)",
                color.white,
              )
            }: VDomModifier
          case false => VDomModifier.empty
        }
      ),
      isVisible = GlobalState.showFilterList,
      initialPosition = position,
      initialWidth = 260,
      initialHeight = 250,
      resizable = false,
      titleModifier = Ownable(implicit ctx =>
        Rx{
          VDomModifier(
            backgroundColor := GlobalState.pageStyle().pageBgColor,
            color.white,
          )
        }),

      bodyModifier = Ownable { implicit ctx =>
        body
      }
    )
  }

  val body = div(
    padding := "5px",

    Components.verticalMenu(
      ViewGraphTransformation.availableTransformations.map { transformation =>
        Components.MenuItem(
          title = VDomModifier(transformation.icon, marginLeft := "5px", marginRight := "5px"),
          description = transformation.description,
          active = GlobalState.graphTransformations.map(_.contains(transformation.transform) ^ transformation.invertedSwitch),
          clickAction = { () =>
            GlobalState.graphTransformations.update { transformations =>
              if (transformations.contains(transformation.transform)) transformations.filter(_ != transformation.transform)
              else transformations.filterNot(transformation.disablesTransform.contains) ++ (transformation.enablesTransform :+ transformation.transform)
            }

            transformation match {
              case ViewGraphTransformation.Deleted.onlyDeleted         => FeatureState.use(Feature.FilterOnlyDeleted)
              case ViewGraphTransformation.Deleted.excludeDeleted      => FeatureState.use(Feature.FilterDeleted)
              case ViewGraphTransformation.Assignments.onlyAssignedTo  => FeatureState.use(Feature.FilterOnlyAssignedTo)
              case ViewGraphTransformation.Assignments.onlyNotAssigned => FeatureState.use(Feature.FilterOnlyNotAssigned)
              case ViewGraphTransformation.Automated.hideTemplates     => FeatureState.use(Feature.FilterAutomationTemplates)
            }
          }
        )
      }
    ),
    div(
      Styles.flex,
      justifyContent.flexEnd,
      button(
        marginTop := "20px",
        cls := "ui compact basic button",
        cursor.pointer,
        Elements.icon(Icons.noFilter),
        span(resetAllFiltersText),
        onClick.stopPropagation.use(GlobalState.defaultTransformations) --> GlobalState.graphTransformations,
        onClick.stopPropagation foreach { FeatureState.use(Feature.ResetFilters) },
      )
    )
  )
}

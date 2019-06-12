package wust.webApp.views

import outwatch.dom.VDomModifier
import wust.webApp.Icons
import wust.webUtil.outwatchHelpers._


case class ViewGraphTransformation(
  transform: UserViewGraphTransformation,
  icon: VDomModifier,
  description: String,
  enablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation],
  disablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation],
  invertedSwitch: Boolean = false, //Filter is active, so enabling it will turn it off
)

object ViewGraphTransformation {
  val allTransformations: List[ViewGraphTransformation] = List(
    //    ViewGraphTransformation.Deleted.inGracePeriod,
    ViewGraphTransformation.Deleted.onlyDeleted,
    ViewGraphTransformation.Deleted.excludeDeleted,
    //    ViewGraphTransformation.Deleted.noDeletedButGraced,
    ViewGraphTransformation.Assignments.onlyAssignedTo,
    ViewGraphTransformation.Assignments.onlyNotAssigned,
    ViewGraphTransformation.Automated.hideTemplates,
    //    Identity,
  )

  val identity = ViewGraphTransformation(
    icon = Icons.noFilter,
    description = "Reset ALL filters",
    transform = GraphOperation.Identity,
  )

  object Deleted {
    val onlyDeleted = ViewGraphTransformation (
      icon = Icons.delete,
      description = "Show only deleted items",
      transform = GraphOperation.OnlyDeletedChildren,
      disablesTransform = List(GraphOperation.ExcludeDeletedChildren)
    )
    val excludeDeleted = ViewGraphTransformation (
      icon = Icons.undelete,
      description = "Show deleted items", // Turns this filter off
      transform = GraphOperation.ExcludeDeletedChildren,
      invertedSwitch = true
    )
  }

  object Automated {
    val hideTemplates = ViewGraphTransformation(
      icon = Icons.automate,
      description = "Show automation templates", // Turns this filter off
      transform = GraphOperation.AutomatedHideTemplates,
      invertedSwitch = true
    )
  }

  object Assignments {
    val onlyAssignedTo = ViewGraphTransformation (
      icon = Icons.task,
      description = s"Show items assigned to: Me",
      transform = GraphOperation.OnlyAssignedTo,
    )
    val onlyNotAssigned = ViewGraphTransformation (
      icon = Icons.task,
      description = "Show unassigned items",
      transform = GraphOperation.OnlyNotAssigned,
    )
  }
}
package wust.webApp.views

import outwatch.dom.VDomModifier
import wust.webApp.Icons
import wust.webUtil.outwatchHelpers._
import wust.util.macros.SubObjects


sealed trait ViewGraphTransformation{
  def transform: UserViewGraphTransformation
  def icon: VDomModifier
  def description: String
  def enablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation]
  def disablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation]
  def invertedSwitch: Boolean = false //Filter is active, so enabling it will turn it off
}

object ViewGraphTransformation {
  val availableTransformations: Array[ViewGraphTransformation] = Array(
      ViewGraphTransformation.Deleted.excludeDeleted,
      ViewGraphTransformation.Deleted.onlyDeleted,
      ViewGraphTransformation.Assignments.onlyAssignedTo,
      ViewGraphTransformation.Assignments.onlyNotAssigned,
      ViewGraphTransformation.Automated.hideTemplates
    )

  object Deleted {
    case object onlyDeleted extends ViewGraphTransformation {
      def icon = Icons.delete
      def description = "Show only deleted items"
      def transform = GraphOperation.OnlyDeletedChildren
      override def disablesTransform = List(GraphOperation.ExcludeDeletedChildren)
    }

    case object excludeDeleted extends ViewGraphTransformation {
      def icon = Icons.undelete
      def description = "Show deleted items" // Turns this filter off
      def transform = GraphOperation.ExcludeDeletedChildren
      override def invertedSwitch = true
    }
  }

  object Automated {
    case object hideTemplates extends ViewGraphTransformation{
      def icon = Icons.automate
      def description = "Show automation templates" // Turns this filter off
      def transform = GraphOperation.AutomatedHideTemplates
      override def invertedSwitch = true
    }
  }

  object Assignments {
    case object onlyAssignedTo extends ViewGraphTransformation {
      def icon = Icons.task
      def description = s"Show items assigned to: Me"
      def transform = GraphOperation.OnlyAssignedTo
    }
    case object onlyNotAssigned extends ViewGraphTransformation {
      def icon = Icons.task
      def description = "Show unassigned items"
      def transform = GraphOperation.OnlyNotAssigned
    }
  }
}

package wust.webApp.state

import flatland._
import acyclic.file
import outwatch.dom._
import outwatch.dom.dsl._
import wust.ids.Feature
import wust.util.macros.SubObjects
import wust.webApp.views.ViewGraphTransformation

case class FeatureDetails(
  title: String,
  description: VDomModifier = VDomModifier.empty
//TODO: link to gif animation
//TODO: link where action can take place (e.g. user profile, specific view)
)

object FeatureDetails {
  def addView(view: String) = FeatureDetails(
    title = s"Add $view View",
    description = s"When creating a new project, select '$view'. Or inside a project: press the '+' next to the tabs and press '$view'."
  )

  def activateFilter(transformation: ViewGraphTransformation) = FeatureDetails(
    title = s"Filter: ${transformation.description}",
    description = span("Open the filter window and click ", b(transformation.description), ".")
  )

  private val mapping: PartialFunction[Feature, FeatureDetails] = {
    import Feature._
    //TODO: ensure match is exhaustive at compile-time!
    {
      case CreateProject => FeatureDetails (
        title = "Create Project",
        description = "Press the 'New Project' button on the start page or in the left sidebar."
      )
      case CreateTaskInChecklist => FeatureDetails(
        title = "Create Task in Checklist",
        description = "Write a task into the input field and press Enter."
      )
      case CheckTask => FeatureDetails (
        title = "Check Task",
        description = "Click on the checkbox of a task to mark it as done."
      )
      case UncheckTask => FeatureDetails (
        title = "Uncheck Task",
        description = "Uncheck an already checked task to recover it."
      )
      case ReorderTaskInChecklist => FeatureDetails (
        title = "Reorder Tasks in Checklist",
        description = "Drag a task to put it at a different position in the list"
      )
      case DeleteTaskInChecklist => FeatureDetails (
        title = "Delete Task in Checklist",
        description = "Click the trash icon to delete a task."
      )
      case CreateTaskInKanban => FeatureDetails (
        title = "Create Task in Kanban",
        description = "Write a task into the input field of a column and press Enter."
      )
      case UndeleteTaskInChecklist => FeatureDetails (
        title = "Undelete Task in Checklist",
        description = "Click the stiped trash icon to delete a task."
      )
      case AddKanbanView => FeatureDetails.addView (
        view = "Kanban"
      )
      case ReorderTaskInKanban => FeatureDetails (
        title = "Reorder Task in Kanban",
        description = "Drag a task to put it at a different position in a column"
      )
      case DragTaskToDifferentColumnInKanban => FeatureDetails (
        title = "Move Task to different Column (Kanban)",
        description = "Drag a task and drop it into a different Column."
      )

      case FilterOnlyDeleted => FeatureDetails.activateFilter (
        transformation = ViewGraphTransformation.Deleted.onlyDeleted
      )
      case FilterDeleted => FeatureDetails.activateFilter (
        transformation = ViewGraphTransformation.Deleted.excludeDeleted
      )
      case FilterOnlyAssignedTo => FeatureDetails.activateFilter (
        transformation = ViewGraphTransformation.Assignments.onlyAssignedTo
      )
      case FilterOnlyNotAssigned => FeatureDetails.activateFilter (
        transformation = ViewGraphTransformation.Assignments.onlyNotAssigned
      )
      case FilterAutomationTemplates => FeatureDetails.activateFilter (
        transformation = ViewGraphTransformation.Automated.hideTemplates
      )
      case ResetFilters => FeatureDetails (
        title = "Reset Filters",
        description = "Open filter window and press button: Reset all filters." //TODO: use button text as variable
      )

      case AddChatView => FeatureDetails.addView(
        view = "Chat"
      )
      case AddChecklistView => FeatureDetails.addView(
        view = "Checklist"
      )
      case EnableBrowserNotifications => FeatureDetails(
        title = "Enable Browser Notifications",
        description = "Click on the Cog (top right) and enable notifications. Your browser should ask you for permission now."
      )
      case ClickLogo => FeatureDetails(
        title = "Click Logo",
        description = "Why did you do that?"
      )
      case SubmitFeedback => FeatureDetails(
        title = "Send Feedback",
        description = "Thank you very much!"
      )
    }
  }

  private val liftedMapping = mapping.lift

  def apply(feature: Feature): FeatureDetails = {
    liftedMapping(feature).getOrElse(FeatureDetails(title = feature.toString))
  }

  def missingDetails = Feature.all.filter(feature => liftedMapping(feature).isEmpty)
}

package wust.webApp.state

import wust.webApp.views._
import flatland._
import acyclic.file
import outwatch.dom._
import outwatch.dom.dsl._
import scala.scalajs.js
import wust.webApp.{ DevOnly, StagingOnly, DebugOnly }
import org.scalajs.dom.console
import wust.ids.{ Feature, View }
import wust.util.macros.SubObjects
import wust.webApp.views.ViewGraphTransformation
import wust.webUtil.outwatchHelpers._
import wust.webApp.Icons

case class FeatureDetails(
  title: String,
  description: VDomModifier = VDomModifier.empty
//TODO: link to gif animation
//TODO: link where action can take place (e.g. user profile, specific view)
)

object FeatureDetails {
  def addView(view: View.Visible) = FeatureDetails(
    title = s"Add ${view.toString} View",
    description = VDomModifier(s"When creating a new project, select ", em(view.toString), ". Or: press ", em(ViewSwitcher.addViewIcon), " next to the view tabs of a project.")
  )

  def activateFilter(transformation: ViewGraphTransformation) = FeatureDetails(
    title = s"${transformation.description} (Filter)",
    description = span("Expand filters and click ", em(transformation.description), ".")
  )

  private val mapping: PartialFunction[Feature, FeatureDetails] = {
    import Feature._
    //TODO: ensure match is exhaustive at compile-time!
    {
      case CreateProject => FeatureDetails (
        title = "Create Project",
        description = VDomModifier("Press the ", em(NewProjectPrompt.newProjectText), " button on the start page or in the left sidebar.")
      )
      case CreateProjectFromExpandedLeftSidebar => FeatureDetails (
        title = "Create Project (Expanded Left Sidebar)",
        description = VDomModifier("Press the ", em(NewProjectPrompt.newProjectText), " button in the expanded left sidebar.")
      )
      case CreateProjectFromCollapsedLeftSidebar => FeatureDetails (
        title = "Create Project (Collapsed Left Sidebar)",
        description = VDomModifier("Press the ", em(NewProjectPrompt.newProjectText), " button in the collapsed left sidebar.")
      )
      case SwitchPageFromExpandedLeftSidebar => FeatureDetails (
        title = "Switch Page (Expanded Left Sidebar)",
        description = VDomModifier("Click a project in the expanded left sidebar.")
      )
      case SwitchPageFromCollapsedLeftSidebar => FeatureDetails (
        title = "Switch Page (Collapsed Left Sidebar)",
        description = VDomModifier("Click a project in the collapsed left sidebar.")
      )

      // Checklist
      case AddChecklistView => FeatureDetails.addView(
        view = View.List
      )
      case CreateTaskInChecklist => FeatureDetails(
        title = "Create Task (Checklist)",
        description = VDomModifier("Type a task in the field and press ", em("Enter"), ".")
      )
      case CheckTask => FeatureDetails (
        title = "Check Task (Checklist)",
        description = "Click on the checkbox of a task to mark it as done."
      )
      case UncheckTask => FeatureDetails (
        title = "Uncheck Task (Checklist)",
        description = "Uncheck an already checked task to recover it."
      )
      case ReorderTaskInChecklist => FeatureDetails (
        title = "Reorder Tasks (Checklist)",
        description = "Drag a task to move it to another position in the list."
      )
      case DeleteTaskInChecklist => FeatureDetails (
        title = "Delete Task (Checklist)",
        description = VDomModifier("Click ", em(Icons.delete), " on the right to delete a task.")
      )
      case UndeleteTaskInChecklist => FeatureDetails (
        title = "Undelete Task (Checklist)",
        description = VDomModifier("Activate filter ",em(ViewGraphTransformation.Deleted.excludeDeleted.description),", then click ", em(Icons.undelete), " on the right to undelete a task.")
      )
      case ExpandTaskInChecklist => FeatureDetails (
        title = "Expand Task (Checklist)",
        description = VDomModifier("Click ", em(Icons.expand), " on a task to show or add sub-tasks.")
      )
      case CreateNestedTaskInChecklist => FeatureDetails (
        title = "Create Sub-Task (Checklist)",
        description = VDomModifier("In an expanded task, type a new task in the field and press ", em("Enter"), ".")
      )

      // Kanban
      case AddKanbanView => FeatureDetails.addView (
        view = View.Kanban
      )
      case CreateTaskInKanban => FeatureDetails (
        title = "Create Task (Kanban)",
        description = VDomModifier(s"At the bottom of a column, click ", em(KanbanView.addCardText), " type a task in the field and press ", em("Enter"), ".")
      )
      case CreateColumnInKanban => FeatureDetails (
        title = "Create Column (Kanban)",
        description = VDomModifier(s"On the right side, click ", em(KanbanView.addColumnText), ", type a task in the field of a column and press ", em("Enter"), ".")
      )
      case ReorderTaskInKanban => FeatureDetails (
        title = "Reorder Tasks (Kanban)",
        description = "Drag a task to move it to another position in the column."
      )
      case DragTaskToDifferentColumnInKanban => FeatureDetails (
        title = "Drag Task to another Column (Kanban)",
        description = "Drag and drop a task to another column."
      )
      case EditColumnInKanban => FeatureDetails (
        title = "Edit Column Title (Kanban)",
        description = VDomModifier("Click on ", em(Icons.edit), " at the top of a column to change its title.")
      )
      case ExpandTaskInKanban => FeatureDetails (
        title = "Expand Task (Kanban)",
        description = VDomModifier("Click ", em(Icons.expand), " on a card to show or add sub-tasks.")
      )
      case CreateNestedTaskInKanban => FeatureDetails (
        title = "Create Sub-Task (Kanban)",
        description = VDomModifier("In an expanded task, type a new task in the field and press ", em("Enter"), ".")
      )
      case ReorderColumnsInKanban => FeatureDetails (
        title = "Reorder Columns (Kanban)",
        description = "Drag a column to move it to another position in the kanban board."
      )
      case NestColumnsInKanban => FeatureDetails (
        title = "Nest Columns (Kanban)",
        description = "Drag a column to move it into another column."
      )

      // Notes
      case AddNotesView => FeatureDetails.addView(
        view = View.Content
      )
      case CreateNoteInNotes => FeatureDetails (
        title = "Create Note (Notes)",
        description = VDomModifier("Type a note in the field and press ", em("Ctrl+Enter"), ". Or: Click somewhere else to save.")
      )

      // Automation
      case CreateAutomationTemplate => FeatureDetails (
        title = "Create automation template",
        description = VDomModifier("Click the ", em(Icons.automate), " icon on a kanban-column or tag in the tag-section. Then click ", em(GraphChangesAutomationUI.createAutomationTemplateText), ".")
      )

      // Filter
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
        description = VDomModifier("Expand filters and press: ", em(FilterWindow.resetAllFiltersText), ".")
      )

      // custom fields, tags, assignments
      case CreateTag => FeatureDetails (
        title = "Create Tag",
        description = VDomModifier("Expand tags and click ", em(TagList.addTagText), ".")
      )
      case NestTagsByDragging => FeatureDetails (
        title = "Nest Tags",
        description = VDomModifier("In the tags section, drag one tag into another.")
      )
      case FilterByTag => FeatureDetails (
        title = "Filter by Tag",
        description = VDomModifier("In the tags section, click the checkbox next to a tag.")
      )
      case FilterByTagWithSubTag => FeatureDetails (
        title = "Filter by Tag with Sub-Tag",
        description = VDomModifier("In the tags section, click the checkbox next to a tag which contains another tag.")
      )
      case TagTaskByDragging => FeatureDetails (
        title = "Tag Task (Drag&Drop)",
        description = "Expand the tags section and drag a tag onto a task."
      )
      case TagTaskWithNestedTagByDragging => FeatureDetails (
        title = "Tag Task with Nested Tag (Drag&Drop)",
        description = "Expand the tags section and drag a nested tag onto a task."
      )
      case AssignTaskByDragging => FeatureDetails (
        title = "Assign Task (Drag&Drop)",
        description = VDomModifier("Drag a user avatar ", Avatar(GlobalState.user.now.toNode)(height := "20px", marginBottom := "-5px", cls := "avatar"), " from the members list (next to the project title) onto a task.")
      )
      case AddCustomFieldToTask => FeatureDetails (
        title = "Add Custom Field to Task",
        description = VDomModifier("Open task in the right sidebar, expand ", em(RightSidebar.propertiesAccordionText), " and press ", em(RightSidebar.addCustomFieldText), ".")
      )

      // Right Sidebar
      case OpenTaskInRightSidebar => FeatureDetails (
        title = "Open Task in Right Sidebar",
        description = "Click on task to open it in the right sidebar."
      )
      case OpenProjectInRightSidebar => FeatureDetails (
        title = "Open Project in Right Sidebar",
        description = "Click on project to open it in the right sidebar."
      )
      case OpenMessageInRightSidebar => FeatureDetails (
        title = "Open Message in Right Sidebar",
        description = "Click on message to open it in the right sidebar."
      )
      // case OpenNoteInRightSidebar => FeatureDetails (
      //   title = "Open Note in Right Sidebar",
      //   description = "Click on note to open it in the right sidebar."
      // )

      // Editing nodes
      case EditTaskInRightSidebar => FeatureDetails (
        title = "Edit Task",
        description = "Click on task to open it in the right sidebar, then click it to edit."
      )
      case EditProjectInRightSidebar => FeatureDetails (
        title = "Edit Project",
        description = "Click on project to open it in the right sidebar, then click it to edit."
      )
      case EditMessageInRightSidebar => FeatureDetails (
        title = "Edit Message",
        description = "Click on message to open it in the right sidebar, then click it to edit."
      )
      // case EditNoteInRightSidebar => FeatureDetails (
      //   title = "Edit Note",
      //   description = "Click on note to open it in the right sidebar, then click it to edit."
      // )

      // Zoom
      case ZoomIntoTask => FeatureDetails (
        title = "Zoom into Task",
        description = VDomModifier("Click on task to open it in the right sidebar, then click ", em(Icons.zoom), ". Or: Double-click on a task.")
      )
      case ZoomIntoMessage => FeatureDetails (
        title = "Zoom into Message",
        description = VDomModifier("Click on message to open it in the right sidebar, then click ", em(Icons.zoom), ". Or: Double-click on a message.")
      )
      case ZoomIntoProject => FeatureDetails (
        title = "Zoom into Project",
        description = VDomModifier("Click on project to open it in the right sidebar, then click ", em(Icons.zoom), ". Or: Double-click on a project.")
      )
      case ZoomIntoNote => FeatureDetails (
        title = "Zoom into Note",
        description = VDomModifier("Inside the note, click on ", em(Icons.zoom), ".")
      )

      // Bookmark
      case BookmarkTask => FeatureDetails (
        title = "Bookmark a Task",
        description = VDomModifier("Zoom into a task, then click ", em(Icons.bookmark), ". Or: drag a task into the left sidebar.")
      )
      case BookmarkMessage => FeatureDetails (
        title = "Bookmark a Message",
        description = VDomModifier("Zoom into a message, then click ", em(Icons.bookmark), ". Or: drag a message into the left sidebar.")
      )
      case BookmarkNote => FeatureDetails (
        title = "Bookmark a Note",
        description = VDomModifier("Zoom into a note, then click ", em(Icons.bookmark), ". Or: drag a note into the left sidebar.")
      )
      case BookmarkProject => FeatureDetails (
        title = "Bookmark a Project",
        description = VDomModifier("Zoom into a project, then click ", em(Icons.bookmark), ". Or: drag a project into the left sidebar.")
      )

      case AddChatView => FeatureDetails.addView(
        view = View.Chat
      )
      case CreateMessageInChat => FeatureDetails(
        title = "Create Message (Chat)",
        description = VDomModifier("Type a message into the field and press ", em("Enter"), "."),
      )
      case ReplyToMessageInChat => FeatureDetails(
        title = "Reply to Message (Chat)",
        description = VDomModifier("Next to a message, click ", em(Icons.reply), " and type another message."),
      )
      case NestMessagesByDragging => FeatureDetails(
        title = "Nest Messages (Chat, Drag&Drop)",
        description = VDomModifier("Drag a message into another message. This is the same as replying to a message."),
      )
      case UnNestMessagesByDragging => FeatureDetails(
        title = "Unnest Messages (Chat, Drag&Drop)",
        description = VDomModifier("Drag a nested message into the background of the chat."),
      )

      case AddDashboardView => FeatureDetails.addView(
        view = View.Dashboard
      )
      case EnableBrowserNotifications => FeatureDetails(
        title = "Enable Browser Notifications",
        description = VDomModifier("Click on ", em(Icons.menu), " (top right) and enable notifications. Your browser should ask you for permission now.")
      )
      case CloseLeftSidebar => FeatureDetails(
        title = "Close left Sidebar",
        description = VDomModifier("Click on ", em(Icons.hamburger), " at the top left of the screen.")
      )
      case OpenLeftSidebar => FeatureDetails(
        title = "Open left Sidebar",
        description = VDomModifier("Click on ", em(Icons.hamburger), " at the top left of the screen.")
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

  def hasDetails(feature: Feature) = mapping.isDefinedAt(feature)

  def apply(feature: Feature): FeatureDetails = {
    liftedMapping(feature).getOrElse(FeatureDetails(title = feature.toString))
  }

  def missingDetails = Feature.all.filter(feature => liftedMapping(feature).isEmpty)

  FeatureState.recentlyUsed.foreach { _ =>
    import FeatureState._
    DebugOnly {
      console.asInstanceOf[js.Dynamic].groupCollapsed(s"Feature Dotgraph")
      console.log(Feature.dotGraph(recentFirstTimeUsed.now, recentlyUsed.now, nextCandidates.now, next.now, f => FeatureDetails(f).title, FeatureDetails.missingDetails))
      console.asInstanceOf[js.Dynamic].groupEnd()
    }
  }
}

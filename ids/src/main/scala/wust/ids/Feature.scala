package wust.ids

import flatland._
import collection.mutable
import wust.util.algorithm
import acyclic.file
import wust.util.macros.SubObjects

// IMPORTANT: renaming and deleting in this file needs a db-migration!

// This file captures the topology of all UI-Features in Woost.
// Features form a Graph, by suggesting other features that should be tried next.
// Features are also categorized by traits.

sealed trait Feature {
  def next: Array[Feature] = Array.empty[Feature]
}

object Feature {
  object Category {
    //TODO: trait for specific categories, like Views (Kanban,Graph), noderoles, or Use-Cases (CRM, Automation)
    //TODO: awards for completing certain feature lists (Kanban, Task)
    sealed trait StartingPoint extends Feature

    sealed trait View extends Feature
    object View {
      sealed trait Checklist extends Feature
      sealed trait Kanban extends Feature
      sealed trait Chat extends Feature
      sealed trait Thread extends Feature
      sealed trait Notes extends Feature
    }

    object Item {
      sealed trait Task extends Feature
      sealed trait Message extends Feature
      sealed trait Note extends Feature
      sealed trait Project extends Feature
      sealed trait Tag extends Feature
    }

    sealed trait Drag extends Feature

    sealed trait Filter extends Feature
    object Filter {
      sealed trait GraphTransformation extends Filter
    }

    sealed trait Automation extends Feature

    sealed trait Setup extends Feature
    sealed trait Basics extends Feature
    sealed trait Plugin extends Feature

    sealed trait Secret extends Feature
  }
  // Context parameters: byDrag:Boolean, inRightSidebar, inView:View, nodeRole:NodeRole

  // zoom into project / task / message / note, zoom deep
  // click into breadcrumbs
  // automation with columns / tags
  // different automation features
  // custom fields
  // edit task/project/message/note
  // create message in task
  // chat: reply, drag messages into each other, drag out to background
  // filters
  // nest tags
  // nest columns
  // add task while filtering for tag / assignment
  // drag tag from one task to another
  // due dates
  // register
  // deleting / undeleting / delete filter
  // (create project with multiple views / add view to existing project with views), switch view
  // remove view
  // deep task nesting, one Feature for each level until ~5
  // all dragActions
  // all stuff from announcekit
  // emojis: send message with emoji, use emoji as project icon
  // assign user
  // invite user to project (via email / via link) (High-Priority -> GROWTH LOOP)
  // expand/collapse left sidebar
  // pageSettingsMenu
  // many things for which we already do Analytics.sendEvent
  // import
  // Login with a second device
  //TODO: enable browser notifications on a second device, mobile device, desktop device
  // third device... extends Category.Secret
  // ctrl+drag

  case object CreateProject extends Category.Item.Project with Category.Basics with Category.StartingPoint { override def next = Array(AddChecklistView, AddKanbanView, AddChatView, AddNotesView, OpenProjectInRightSidebar, CreateSubProjectFromDashboard) }
  case object CreateSubProjectFromDashboard extends Category.Item.Project { override def next = Array(ZoomIntoProject) }

  // Basics
  case object CloseLeftSidebar extends Category.Basics with Category.StartingPoint { override def next = Array(SwitchPageFromCollapsedLeftSidebar, CreateProjectFromCollapsedLeftSidebar, OpenLeftSidebar) }
  case object OpenLeftSidebar extends Category.Basics { override def next = Array(SwitchPageFromExpandedLeftSidebar, CreateProjectFromExpandedLeftSidebar, CloseLeftSidebar) }
  case object CreateProjectFromExpandedLeftSidebar extends Category.Basics {}
  case object CreateProjectFromCollapsedLeftSidebar extends Category.Basics {}
  case object SwitchPageFromExpandedLeftSidebar extends Category.Basics {}
  case object SwitchPageFromCollapsedLeftSidebar extends Category.Basics {}

  case object OpenProjectInRightSidebar extends Category.Basics with Category.Item.Project { override def next = Array(EditProjectInRightSidebar, ZoomIntoProject) }
  case object OpenTaskInRightSidebar extends Category.Basics with Category.Item.Task { override def next = Array(EditTaskInRightSidebar, ZoomIntoTask) }
  case object OpenMessageInRightSidebar extends Category.Basics with Category.Item.Message { override def next = Array(EditMessageInRightSidebar, ZoomIntoMessage) }
  case object OpenNoteInRightSidebar extends Category.Basics with Category.Item.Note { override def next = Array(EditNoteInRightSidebar, ZoomIntoNote) }

  case object EditProjectInRightSidebar extends Category.Basics with Category.Item.Project
  case object EditTaskInRightSidebar extends Category.Basics with Category.Item.Task
  case object EditMessageInRightSidebar extends Category.Basics with Category.Item.Message
  case object EditNoteInRightSidebar extends Category.Basics with Category.Item.Note

  case object ZoomIntoProject extends Category.Basics with Category.Item.Project { override def next = Array(BookmarkProject) }
  case object ZoomIntoTask extends Category.Basics with Category.Item.Task { override def next = Array(BookmarkTask) }
  case object ZoomIntoMessage extends Category.Basics with Category.Item.Message { override def next = Array(BookmarkMessage) }
  case object ZoomIntoNote extends Category.Basics with Category.Item.Note { override def next = Array(BookmarkNote, OpenNoteInRightSidebar) }

  case object BookmarkProject extends Category.Basics with Category.Item.Project
  case object BookmarkTask extends Category.Basics with Category.Item.Task
  case object BookmarkMessage extends Category.Basics with Category.Item.Message
  case object BookmarkNote extends Category.Basics with Category.Item.Note

  // Task
  case object AddCustomFieldToTask extends Category.Item.Task
  case object AssignTaskByDragging extends Category.Item.Task with Category.Drag { override def next = Array(FilterOnlyAssignedTo, FilterOnlyNotAssigned) }

  // Chat
  case object AddChatView extends Category.View { override def next = Array(CreateMessageInChat) }
  case object CreateMessageInChat extends Category.View.Chat with Category.Item.Message { override def next = Array(ReplyToMessageInChat, OpenMessageInRightSidebar, TagMessageByDragging) }
  case object ReplyToMessageInChat extends Category.View.Chat with Category.Item.Message { override def next = Array(NestMessagesByDragging, OpenMessageInRightSidebar, TagMessageByDragging) }
  case object NestMessagesByDragging extends Category.View.Chat with Category.Item.Message with Category.Drag { override def next = Array(ZoomIntoMessage, UnNestMessagesByDragging) }
  case object UnNestMessagesByDragging extends Category.View.Chat with Category.Item.Message with Category.Drag 
  // reply -> zoom

  // ViewSwitcher
  case object SwitchToChecklistInPageHeader extends Category.View with Category.Secret { override def next = AddChecklistView.next }
  case object SwitchToKanbanInPageHeader extends Category.View with Category.Secret { override def next = AddKanbanView.next }
  case object SwitchToChatInPageHeader extends Category.View with Category.Secret { override def next = AddChatView.next }

  case object SwitchToChecklistInRightSidebar extends Category.View with Category.Secret { override def next = AddChecklistView.next }
  case object SwitchToKanbanInRightSidebar extends Category.View with Category.Secret { override def next = AddKanbanView.next }
  case object SwitchToChatInRightSidebar extends Category.View with Category.Secret { override def next = AddChatView.next }

  // Checklist
  case object AddChecklistView extends Category.View with Category.View.Checklist { override def next = Array(CreateTaskInChecklist) }
  case object CreateTaskInChecklist extends Category.View.Checklist with Category.Item.Task { override def next = Array(CheckTask, ReorderTaskInChecklist, CreateNestedTaskInChecklist, OpenTaskInRightSidebar, CreateTag, TagTaskByDragging, AssignTaskByDragging) }
  case object CreateNestedTaskInChecklist extends Category.View.Checklist with Category.Item.Task  //TODO: sub-sub-task, sub-sub-sub-task, ....
  //TODO:Expand Task
  //TODO:Drag task into other Task
  //TODO:Check sub-task to see progress bar
  case object CheckTask extends Category.View.Checklist with Category.Item.Task { override def next = Array(UncheckTask, ReorderTaskInChecklist) }
  case object UncheckTask extends Category.View.Checklist with Category.Item.Task { override def next = Array(DeleteTaskInChecklist) }
  case object ReorderTaskInChecklist extends Category.View.Checklist with Category.Item.Task { /* override def next = Array() */ }
  case object DeleteTaskInChecklist extends Category.View.Checklist with Category.Item.Task { override def next = Array(FilterDeleted, UndeleteTaskInChecklist, FilterOnlyDeleted) }
  case object UndeleteTaskInChecklist extends Category.View.Checklist with Category.Item.Task {}

  // Kanban
  case object AddKanbanView extends Category.View with Category.View.Kanban { override def next = Array(CreateColumnInKanban, CreateTaskInKanban) }
  case object CreateColumnInKanban extends Category.View.Kanban { override def next = Array(CreateTaskInKanban, EditColumnInKanban, ReorderColumnsInKanban, NestColumnsInKanban, CreateAutomationTemplateInKanban) }
  case object EditColumnInKanban extends Category.View.Kanban
  case object ReorderColumnsInKanban extends Category.View.Kanban
  case object NestColumnsInKanban extends Category.View.Kanban
  case object CreateTaskInKanban extends Category.View.Kanban with Category.Item.Task { override def next = Array(ReorderTaskInKanban, DragTaskToDifferentColumnInKanban, CreateNestedTaskInKanban, TagTaskByDragging, AssignTaskByDragging, AddCustomFieldToTask, CreateAutomationTemplateInKanban) }
  case object CreateNestedTaskInKanban extends Category.View.Kanban with Category.Item.Task
  case object ReorderTaskInKanban extends Category.View.Kanban with Category.Item.Task {}
  case object DragTaskToDifferentColumnInKanban extends Category.View.Kanban with Category.Item.Task with Category.Drag {}

  // Notes
  case object AddNotesView extends Category.View { override def next = Array(CreateNoteInNotes) }
  case object CreateNoteInNotes extends Category.View.Notes with Category.Item.Note { override def next = Array(ZoomIntoNote, EditNoteInRightSidebar, TagNoteByDragging) }

  // Filters
  case object FilterOnlyDeleted extends Category.Filter.GraphTransformation { override def next = Array(ResetFilters) }
  case object FilterDeleted extends Category.Filter.GraphTransformation { override def next = Array(UndeleteTaskInChecklist, /*UndeleteTaskInKanban, UndeleteMessageInChat,*/ ResetFilters) }
  case object FilterOnlyAssignedTo extends Category.Filter.GraphTransformation { override def next = Array(ResetFilters) }
  case object FilterOnlyNotAssigned extends Category.Filter.GraphTransformation { override def next = Array(ResetFilters) }
  case object FilterAutomationTemplates extends Category.Filter.GraphTransformation { override def next = Array(ResetFilters) }
  case object ResetFilters extends Category.Filter

  // Tags
  case object CreateTag extends Category.Item.Tag { override def next = Array(TagTaskByDragging, TagMessageByDragging, TagNoteByDragging, FilterByTag, NestTagsByDragging, TagTaskWithNestedTagByDragging, FilterByNestedTag) }
  case object TagTaskByDragging extends Category.Item.Task with Category.Item.Tag with Category.Drag { override def next = Array(FilterByTag) }
  case object TagTaskWithNestedTagByDragging extends Category.Item.Task with Category.Item.Tag with Category.Drag { override def next = Array(FilterByNestedTag) }
  case object TagMessageByDragging extends Category.Item.Message with Category.Item.Tag with Category.Drag { override def next = Array(FilterByTag) }
  case object TagNoteByDragging extends Category.Item.Note with Category.Item.Tag with Category.Drag { override def next = Array(FilterByTag) }
  case object FilterByTag extends Category.Filter with Category.Item.Tag { override def next = Array(NestTagsByDragging, ResetFilters) }
  case object NestTagsByDragging extends Category.Item.Tag with Category.Drag { override def next = Array(TagTaskWithNestedTagByDragging, FilterByNestedTag) }
  case object FilterByNestedTag extends Category.Filter with Category.Item.Tag{ override def next = Array(ResetFilters) }

  // Automation
  case object CreateAutomationTemplateInKanban extends Category.Automation with Category.View.Kanban { override def next = Array(FilterAutomationTemplates) }

  case object ClickLogo extends Category.Secret
  case object SubmitFeedback extends Category.Secret

  case object EnableBrowserNotifications extends Category.Setup with Category.StartingPoint

  case object EnableSlackPlugin extends Category.Plugin with Category.Secret

  val all = SubObjects.all[Feature]
  val startingPoints = SubObjects.all[Category.StartingPoint].sortBy(-_.next.length)
  val secrets = SubObjects.all[Category.Secret]

  def reachable: Set[Feature] = {
    var visited = Set.empty[Feature]
    dfs(startingPoints.foreachElement, visited += _)
    visited
  }

  def unreachable = {
    val predecessorDegree = mutable.HashMap.empty[Feature, Int].withDefaultValue(0)
    all.foreach { feature =>
      feature.next.foreach { nextFeature =>
        predecessorDegree(nextFeature) += 1
      }
    }
    val candidates = all.toSet -- reachable -- secrets
    candidates.toSeq.sortBy(predecessorDegree)
  }

  def selfLoops = {
    all.filter(f => f.next.contains(f))
  }

  //TODO: create NestedArrayInt of features for faster traversal
  @inline def dfs(
    starts: (Feature => Unit) => Unit,
    processVertex: Feature => Unit
  ): Unit = {
    algorithm.dfs.withManualSuccessors(
      starts = enqueue => starts(feature => enqueue(all.indexOf(feature))),
      size = all.length,
      successors = idx => f => all(idx).next.foreachElement{ feature => f(all.indexOf(feature)) },
      processVertex = idx => processVertex(all(idx))
    )
  }

  @inline def bfs(
    starts: (Feature => Unit) => Unit,
    processVertex: Feature => Unit
  ): Unit = {
    val visited = mutable.HashSet.empty[Feature]
    val queue = mutable.Queue.empty[Feature]
    starts(queue += _)
    while (queue.nonEmpty) {
      val v = queue.dequeue()
      visited += v
      processVertex(v)
      v.next.foreach { n =>
        if (!visited(n)) queue += n
      }
    }
  }

  def dotGraph(recentFirstTimeUsed:Seq[Feature], recentlyUsed:Seq[Feature], nextCandidates:Set[Feature], next: Seq[Feature]): String = {
    val usedColor = "deepskyblue"
    val unusedColor = "azure4"
    val alreadyUsed = recentFirstTimeUsed.toSet

    val builder = new StringBuilder
    builder ++= s"""digraph features {\n"""
    builder ++= s"""rankdir = "LR"\n"""
    builder ++= s"""node [shape=box]\n"""
    // vertices
    Feature.all.foreachElement { feature =>
      val isStart = if(Feature.startingPoints.contains(feature)) ",color=deepskyblue,penwidth=3,peripheries=2" else ""
      val isSecret = if(Feature.secrets.contains(feature)) """,style=dashed""" else ""
      val usedStatus = if(alreadyUsed.contains(feature))
      s",style=filled,fillcolor=lightskyblue,fontcolor=darkslategray"
      else s",style=filled,fillcolor=lightgray,fontcolor=darkslategray"
      val isNextCandidate = if(nextCandidates.contains(feature)) s""",color=deepskyblue,penwidth=3""" else ""
      val isRecent = if(recentlyUsed.contains(feature)) s""",fillcolor=lightsteelblue""" else ""
      val isSuggested = if(next.contains(feature)) s""",color=limegreen,penwidth=${1+(next.length-next.indexOf(feature))}""" else ""
      builder ++= s"""${feature.toString} [label="${feature.toString}"${isStart}${isSecret}${usedStatus}${isNextCandidate}${isRecent}${isSuggested}]\n"""
    }
    // eges
    Feature.all.foreachElement { feature =>
      feature.next.foreachElement { nextFeature =>
        val usedStatus = if(!alreadyUsed(feature) && !alreadyUsed(nextFeature)) s"color=$unusedColor" else s"color=$usedColor"
        builder ++= s"""${feature.toString} -> ${nextFeature.toString} [$usedStatus]\n"""
      }
    }

    def subgraph(name:String, vertices:Seq[Feature]):String = {
      s"""subgraph "cluster_${name}" {
            style = filled
            label = "${name}"
            fillcolor = aliceblue
            ${vertices.mkString(";")}
          }"""
    }

    builder ++= subgraph("View.Checklist", SubObjects.all[Feature.Category.View.Checklist])
    builder ++= subgraph("View.Kanban", SubObjects.all[Feature.Category.View.Kanban])
    builder ++= subgraph("View.Chat", SubObjects.all[Feature.Category.View.Chat])
    builder ++= subgraph("View.Notes", SubObjects.all[Feature.Category.View.Notes])

    // builder ++= subgraph("Filter", SubObjects.all[Feature.Category.Filter])
    // builder ++= subgraph("Item.Tag", SubObjects.all[Feature.Category.Item.Tag])
    // builder ++= subgraph("Drag", SubObjects.all[Feature.Category.Drag])

    // builder ++= subgraph("Item.Task", SubObjects.all[Feature.Category.Item.Task])

    builder ++= "}\n"

    builder.result()
  }
}

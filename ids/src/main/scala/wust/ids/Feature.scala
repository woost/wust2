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

//TODO: enable scala unused compiler flag for only this file
//TODO: Separate required features from suggested features?

//TODO: Interesting Analytics:
// Histogram of how many users reached how much percent of features

sealed trait Feature {
  def next: Array[Feature] = Array.empty[Feature]
  def requiresAll: Array[Feature] = Array.empty[Feature]
  def requiresAny: Array[Feature] = Array.empty[Feature]
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

    sealed trait Automation extends Power with Feature

    sealed trait Setup extends Feature
    sealed trait Basics extends Feature
    sealed trait Power extends Feature

    sealed trait Plugin extends Feature

    sealed trait Secret extends Feature
  }
  // Context parameters: byDrag:Boolean, inRightSidebar, inView:View, nodeRole:NodeRole

  // zoom into project / task / message / note, zoom deep
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
  // invite user to project (via email / via link) (High-Priority -> GROWTH LOOP)
  // expand/collapse left sidebar
  // pageSettingsMenu
  // import
  // Login with a second device
  //TODO: enable browser notifications on a second device, mobile device, desktop device
  // third device... extends Category.Secret
  // ctrl+drag
  // Use markdown
  // share tags between projects
  // select multiple views with ctrl

  case object CreateProject extends Category.Item.Project with Category.Basics with Category.StartingPoint { override def next = Array(AddChecklistView, AddKanbanView, AddChatView, AddNotesView, AddDashboardView, OpenProjectInRightSidebar) }
  case object AddDashboardView extends Category.View { override def next = Array(CreateSubProjectFromDashboard) }
  case object CreateSubProjectFromDashboard extends Category.Item.Project { override def next = Array(ZoomIntoProject) }
  case object CreateProjectFromWelcomeView extends Category.Item.Project with Category.Secret { override def next = CreateProject.next }

  // Basics
  case object CloseLeftSidebar extends Category.Basics with Category.StartingPoint { override def next = Array(SwitchPageFromCollapsedLeftSidebar, CreateProjectFromCollapsedLeftSidebar, OpenLeftSidebar) }
  case object OpenLeftSidebar extends Category.Basics { override def next = Array(SwitchPageFromExpandedLeftSidebar, CreateProjectFromExpandedLeftSidebar, CloseLeftSidebar) }
  case object CreateProjectFromExpandedLeftSidebar extends Category.Basics with Category.Secret {}
  case object CreateProjectFromCollapsedLeftSidebar extends Category.Basics with Category.Secret {}
  case object SwitchPageFromExpandedLeftSidebar extends Category.Basics {}
  case object SwitchPageFromCollapsedLeftSidebar extends Category.Basics {}

  case object ClickBreadcrumb extends Category.Basics with Category.Secret

  case object OpenProjectInRightSidebar extends Category.Basics with Category.Item.Project { override def requiresAny = Array(CreateProject, CreateProjectFromCollapsedLeftSidebar, CreateProjectFromExpandedLeftSidebar, CreateProjectFromWelcomeView, CreateSubProjectFromDashboard); override def next = Array(EditProjectInRightSidebar, ZoomIntoProject) }
  case object OpenTaskInRightSidebar extends Category.Basics with Category.Item.Task { override def requiresAny = Array(CreateTaskInChecklist, CreateTaskInKanban); override def next = Array(EditTaskInRightSidebar, ZoomIntoTask) }
  case object OpenMessageInRightSidebar extends Category.Basics with Category.Item.Message { override def requiresAny = Array(CreateMessageInChat /*TODO: CreateMessageInThread */ ); override def next = Array(EditMessageInRightSidebar, ZoomIntoMessage) }
  // case object OpenNoteInRightSidebar extends Category.Basics with Category.Item.Note { override def next = Array(EditNoteInRightSidebar, ZoomIntoNote) }

  case object EditProjectInRightSidebar extends Category.Basics with Category.Item.Project { override def requiresAll = Array(OpenProjectInRightSidebar) }
  case object EditTaskInRightSidebar extends Category.Basics with Category.Item.Task { override def requiresAll = Array(OpenTaskInRightSidebar); override def requiresAny = Array(CreateTaskInChecklist, CreateTaskInKanban) }
  case object EditMessageInRightSidebar extends Category.Basics with Category.Item.Message { override def requiresAll = Array(OpenMessageInRightSidebar) }
  // case object EditNoteInRightSidebar extends Category.Basics with Category.Item.Note { override def requiresAll = Array(OpenNoteInRightSidebar) }

  case object ZoomIntoProject extends Category.Basics with Category.Item.Project { override def requiresAny = Array(CreateProject, CreateProjectFromCollapsedLeftSidebar, CreateProjectFromExpandedLeftSidebar, CreateProjectFromWelcomeView, CreateSubProjectFromDashboard); override def next = Array(BookmarkProject) }
  case object ZoomIntoTask extends Category.Basics with Category.Item.Task { override def requiresAny = Array(CreateTaskInChecklist, CreateTaskInKanban); override def next = Array(BookmarkTask) }
  case object ZoomIntoMessage extends Category.Basics with Category.Item.Message { override def requiresAny = Array(CreateMessageInChat); override def next = Array(BookmarkMessage) } //TODO: requiresAny CreateMessageInThread
  case object ZoomIntoNote extends Category.Basics with Category.Item.Note { override def requiresAny = Array(CreateNoteInNotes); override def next = Array(BookmarkNote) }

  case object BookmarkProject extends Category.Basics with Category.Item.Project { override def requiresAll = Array(ZoomIntoProject) }
  case object BookmarkTask extends Category.Basics with Category.Item.Task { override def requiresAll = Array(ZoomIntoTask) }
  case object BookmarkMessage extends Category.Basics with Category.Item.Message { override def requiresAll = Array(ZoomIntoMessage) }
  case object BookmarkNote extends Category.Basics with Category.Item.Note { override def requiresAll = Array(ZoomIntoNote) }

  // Task
  case object AddCustomFieldToTask extends Category.Item.Task { override def requiresAll = Array(OpenTaskInRightSidebar) }
  case object AssignTaskByDragging extends Category.Item.Task with Category.Drag { override def requiresAll = Array(InviteMember); override def requiresAny = Array(CreateTaskInKanban, CreateTaskInChecklist); override def next = Array(FilterOnlyAssignedTo, FilterOnlyNotAssigned) }

  // Chat
  case object AddChatView extends Category.View { override def next = Array(CreateMessageInChat) }
  case object CreateMessageInChat extends Category.View.Chat with Category.Item.Message { override def requiresAny = Array(AddChatView, SwitchToChatInPageHeader, SwitchToChatInRightSidebar); override def next = Array(ReplyToMessageInChat, OpenMessageInRightSidebar, TagMessageByDragging) }
  case object ReplyToMessageInChat extends Category.View.Chat with Category.Item.Message { override def requiresAll = Array(CreateMessageInChat /* TODO: ,CreateMessageInThread */ ); override def next = Array(NestMessagesByDragging, OpenMessageInRightSidebar, TagMessageByDragging) }
  case object NestMessagesByDragging extends Category.View.Chat with Category.Item.Message with Category.Drag { override def requiresAll = Array(ReplyToMessageInChat /* TODO: ,CreateMessageInThread */ ); override def next = Array(ZoomIntoMessage, UnNestMessagesByDragging) }
  case object UnNestMessagesByDragging extends Category.View.Chat with Category.Item.Message with Category.Drag { override def requiresAll = Array(NestMessagesByDragging); }
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
  case object CreateTaskInChecklist extends Category.View.Checklist with Category.Item.Task { override def requiresAny = Array(AddChecklistView, SwitchToChecklistInPageHeader, SwitchToChecklistInRightSidebar); override def next = Array(CheckTask, ReorderTaskInChecklist, OpenTaskInRightSidebar, CreateTag, TagTaskByDragging, ExpandTaskInChecklist, AssignTaskByDragging) }
  case object ExpandTaskInChecklist extends Category.View.Checklist with Category.Item.Task with Category.Power { override def requiresAll = Array(CreateNestedTaskInChecklist); override def next = Array(CreateNestedTaskInChecklist) } //TODO: drag task into other task
  case object CreateNestedTaskInChecklist extends Category.View.Checklist with Category.Item.Task with Category.Power { override def requiresAll = Array(CreateTaskInChecklist, OpenTaskInRightSidebar); override def next = Array(ExpandTaskInChecklist) } //TODO: sub-sub-task, sub-sub-sub-task, ....
  //TODO:Drag task into other Task
  //TODO:Check sub-task to see progress bar
  case object CheckTask extends Category.View.Checklist with Category.Item.Task { override def requiresAny = Array(CreateTaskInChecklist, CreateNestedTaskInChecklist, CreateNestedTaskInChecklist); override def next = Array(UncheckTask, ReorderTaskInChecklist) }
  case object UncheckTask extends Category.View.Checklist with Category.Item.Task { override def requiresAll = Array(CheckTask); override def next = Array(DeleteTaskInChecklist) }
  case object ReorderTaskInChecklist extends Category.View.Checklist with Category.Item.Task { override def requiresAll = Array(CreateTaskInChecklist) }
  case object DeleteTaskInChecklist extends Category.View.Checklist with Category.Item.Task { override def requiresAll = Array(CreateTaskInChecklist); override def next = Array(FilterDeleted, UndeleteTaskInChecklist, FilterOnlyDeleted) }
  case object UndeleteTaskInChecklist extends Category.View.Checklist with Category.Item.Task { override def requiresAll = Array(DeleteTaskInChecklist); override def requiresAny = Array(FilterDeleted, FilterOnlyDeleted) }

  // Kanban
  case object AddKanbanView extends Category.View with Category.View.Kanban { override def next = Array(CreateColumnInKanban, CreateTaskInKanban) }
  case object CreateColumnInKanban extends Category.View.Kanban { override def requiresAny = Array(AddKanbanView, SwitchToKanbanInPageHeader, SwitchToKanbanInRightSidebar); override def next = Array(CreateTaskInKanban, EditColumnInKanban, ReorderColumnsInKanban, NestColumnsInKanban, CreateAutomationTemplate) }
  case object EditColumnInKanban extends Category.View.Kanban { override def requiresAll = Array(CreateColumnInKanban) }
  case object ReorderColumnsInKanban extends Category.View.Kanban { override def requiresAll = Array(CreateColumnInKanban) }
  case object NestColumnsInKanban extends Category.View.Kanban with Category.Power { override def requiresAll = Array(CreateColumnInKanban, ReorderColumnsInKanban) }
  case object CreateTaskInKanban extends Category.View.Kanban with Category.Item.Task { override def requiresAny = Array(AddKanbanView, SwitchToKanbanInPageHeader, SwitchToKanbanInRightSidebar); override def next = Array(ReorderTaskInKanban, DragTaskToDifferentColumnInKanban, OpenTaskInRightSidebar, CreateNestedTaskInKanban, TagTaskByDragging, AssignTaskByDragging, AddCustomFieldToTask, CreateAutomationTemplate) }
  case object ExpandTaskInKanban extends Category.View.Kanban with Category.Item.Task with Category.Power { override def requiresAll = Array(CreateNestedTaskInKanban); override def next = Array(CreateNestedTaskInKanban) } //TODO: drag task into other task
  case object CreateNestedTaskInKanban extends Category.View.Kanban with Category.Item.Task with Category.Power { override def requiresAll = Array(OpenTaskInRightSidebar, CreateTaskInKanban); override def next = Array(ExpandTaskInKanban) }
  case object ReorderTaskInKanban extends Category.View.Kanban with Category.Item.Task { override def requiresAll = Array(CreateTaskInKanban); }
  case object DragTaskToDifferentColumnInKanban extends Category.View.Kanban with Category.Item.Task with Category.Drag { override def requiresAll = Array(CreateTaskInKanban); }

  // Notes
  case object AddNotesView extends Category.View { override def next = Array(CreateNoteInNotes) }
  case object CreateNoteInNotes extends Category.View.Notes with Category.Item.Note { override def requiresAll = Array(AddNotesView); override def next = Array(ZoomIntoNote, EditNote, TagNoteByDragging) }
  case object EditNote extends Category.Basics with Category.Item.Note { override def requiresAll = Array(CreateNoteInNotes) }

  // Filters
  case object FilterOnlyDeleted extends Category.Filter.GraphTransformation { override def requiresAny = Array(DeleteTaskInChecklist); override def next = Array(ResetFilters) }
  case object FilterDeleted extends Category.Filter.GraphTransformation { override def requiresAny = Array(DeleteTaskInChecklist); override def next = Array(UndeleteTaskInChecklist, /*UndeleteTaskInKanban, UndeleteMessageInChat,*/ ResetFilters) }
  case object FilterOnlyAssignedTo extends Category.Filter.GraphTransformation { override def requiresAny = Array(AssignTaskByDragging); override def next = Array(ResetFilters) }
  case object FilterOnlyNotAssigned extends Category.Filter.GraphTransformation { override def requiresAny = Array(AssignTaskByDragging); override def next = Array(ResetFilters) }
  case object FilterAutomationTemplates extends Category.Filter.GraphTransformation with Category.Power { override def requiresAny = Array(CreateAutomationTemplate); override def next = Array(ResetFilters) }
  case object ResetFilters extends Feature { override def requiresAny = SubObjects.all[Category.Filter].asInstanceOf[Array[Feature]] }
  case object FilterByTag extends Category.Filter { override def requiresAll = Array(TagTaskByDragging); override def next = Array(NestTagsByDragging, ResetFilters) }
  case object FilterByTagWithSubTag extends Category.Filter with Category.Power { override def requiresAll = Array(TagTaskWithNestedTagByDragging); override def next = Array(ResetFilters) }

  // Tags
  case object CreateTag extends Category.Item.Tag { override def next = Array(TagTaskByDragging, TagMessageByDragging, TagNoteByDragging, FilterByTag, NestTagsByDragging, TagTaskWithNestedTagByDragging, FilterByTagWithSubTag) }
  case object TagTaskByDragging extends Category.Item.Task with Category.Item.Tag with Category.Drag { override def requiresAny = Array(CreateTaskInChecklist, CreateTaskInKanban); override def requiresAll = Array(CreateTag); override def next = Array(FilterByTag) }
  case object TagTaskWithNestedTagByDragging extends Category.Item.Task with Category.Item.Tag with Category.Drag with Category.Power { override def requiresAny = TagTaskByDragging.requiresAny; override def requiresAll = Array(NestTagsByDragging); override def next = Array(FilterByTagWithSubTag) }
  case object TagMessageByDragging extends Category.Item.Message with Category.Item.Tag with Category.Drag { override def requiresAny = Array(CreateMessageInChat); override def requiresAll = Array(CreateTag); override def next = Array(FilterByTag) } // TODO: requiresAny: CreateMessageInThread
  case object TagNoteByDragging extends Category.Item.Note with Category.Item.Tag with Category.Drag { override def requiresAny = Array(CreateNoteInNotes); override def requiresAll = Array(CreateTag); override def next = Array(FilterByTag) }
  case object NestTagsByDragging extends Category.Item.Tag with Category.Drag with Category.Power { override def requiresAll = Array(CreateTag); override def next = Array(TagTaskWithNestedTagByDragging, FilterByTagWithSubTag) }

  // Automation
  case object CreateAutomationTemplate extends Category.Automation with Category.Item.Tag with Category.View.Kanban { override def requiresAny = Array(CreateColumnInKanban, CreateTag); override def next = Array(FilterAutomationTemplates) }

  case object InviteMember extends Category.Secret
  case object ChangeAccessLevel extends Category.Secret
  case object ShareLink extends Category.Secret
  case object AcceptInvite extends Category.Secret
  case object IgnoreInvite extends Category.Secret
  case object ClickLogo extends Category.Secret
  case object SubmitFeedback extends Category.Secret
  case object ClickSignupInWelcomeView extends Category.Secret
  case object ClickSignupInAuthStatus extends Category.Secret
  case object ClickLoginInAuthStatus extends Category.Secret
  case object ClickLogoutInAuthStatus extends Category.Secret
  case object ClickAvatarInAuthStatus extends Category.Secret
  case object ConvertNode extends Category.Secret
  case object Login extends Category.Secret
  case object Signup extends Category.Secret

  case object EnableBrowserNotifications extends Category.Setup with Category.StartingPoint

  case object EnableSlackPlugin extends Category.Plugin with Category.Secret

  val all = SubObjects.all[Feature]
  val startingPoints = SubObjects.all[Category.StartingPoint].sortBy(-_.next.length)
  val secrets = SubObjects.all[Category.Secret]
  val secretsSet:Set[Feature] = secrets.toSet
  val power = SubObjects.all[Category.Power]
  val allWithoutSecrets = all diff secrets
  val allWithoutSecretsSet = allWithoutSecrets.toSet

  def reachable: Set[Feature] = {
    var visited = Set.empty[Feature]
    dfs(startingPoints.foreachElement, visited += _)
    visited
  }

  def unreachable = {
    val predecessorDegree = mutable.HashMap.empty[Feature, Int].withDefaultValue(0)
    all.foreach { prevFeature =>
      prevFeature.next.foreach { nextFeature =>
        predecessorDegree(nextFeature) += 1
      }
    }
    val candidates = all.toSet -- reachable -- secrets
    candidates.toSeq.sortBy(predecessorDegree)
  }

  def selfLoops = {
    all.filter(f => f.next.contains(f)) ++
    all.filter(f => f.requiresAny.contains(f)) ++
    all.filter(f => f.requiresAll.contains(f))
  }

  lazy val predecessors: collection.Map[Feature, Array[Feature]] = {
    val map = mutable.HashMap.empty[Feature, Array[Feature]].withDefaultValue(Array.empty)
    all.foreach { prevFeature =>
      prevFeature.next.foreach { nextFeature =>
        map(nextFeature) = map(nextFeature) :+ prevFeature
      }
    }
    map
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

  @inline def dfsBack(
    starts: (Feature => Unit) => Unit,
    processVertex: Feature => Unit
  ): Unit = {
    algorithm.dfs.withManualSuccessors(
      starts = enqueue => starts(feature => enqueue(all.indexOf(feature))),
      size = all.length,
      successors = idx => f => predecessors(all(idx)).foreachElement{ feature => f(all.indexOf(feature)) },
      processVertex = idx => processVertex(all(idx))
    )
  }

  @inline def dfsExists(
    starts: (Feature => Unit) => Unit,
    isFound: Feature => Boolean
  ): Boolean = {
    var notFound = true
    flatland.depthFirstSearchGeneric(
      vertexCount = all.length,
      foreachSuccessor = (idx, f) => all(idx).next.foreachElement{ feature => f(all.indexOf(feature)) },
      init = (stack, _) => starts(feature => stack.push(all.indexOf(feature))),
      processVertex = elem => if (isFound(all(elem))) notFound = false,
      loopConditionGuard = condition => notFound && condition()
    )
    !notFound
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

  def dotGraph(recentFirstTimeUsed: Seq[Feature], recentlyUsed: Seq[Feature], nextCandidates: Set[Feature], next: Seq[Feature], getTitle: Feature => String = _.toString, missingDetails:Seq[Feature] = Nil): String = {
    val usedColor = "deepskyblue"
    val unusedColor = "azure4"
    val alreadyUsed = recentFirstTimeUsed.toSet

    val builder = new StringBuilder
    builder ++= s"""digraph features {\n"""
    builder ++= s"""rankdir = "LR"\n"""
    builder ++= s"""node [shape=box]\n"""
    // vertices
    Feature.all.foreachElement { feature =>
      val isStart = if (Feature.startingPoints.contains(feature)) ",color=deepskyblue,penwidth=3,peripheries=2" else ""
      val isSecret = if (Feature.secrets.contains(feature)) """,style=dashed""" else ""
      val isPower = if (Feature.power.contains(feature)) """,fontcolor="#6636B7",fontsize=22""" else ""
      val hasMissingDetails = if (missingDetails.contains(feature)) """,fontcolor=red""" else ""
      val usedStatus = if (alreadyUsed.contains(feature))
        s",style=filled,fillcolor=lightskyblue,fontcolor=darkslategray"
      else s",style=filled,fillcolor=lightgray,fontcolor=darkslategray"
      val isNextCandidate = if (nextCandidates.contains(feature)) s""",color=deepskyblue,penwidth=3""" else ""
      val isRecent = if (recentlyUsed.contains(feature)) s""",fillcolor=lightsteelblue""" else ""
      val isSuggested = if (next.contains(feature)) s""",color=limegreen,penwidth=${1 + (next.length - next.indexOf(feature))}""" else ""
      builder ++= s"""${feature.toString} [label="${getTitle(feature)}"${isStart}${isSecret}${usedStatus}${isNextCandidate}${isRecent}${isSuggested}${isPower}${hasMissingDetails}]\n"""
    }
    // eges
    Feature.all.foreachElement { feature =>
      feature.next.foreachElement { nextFeature =>
        val usedStatus = if (alreadyUsed(feature) && alreadyUsed(nextFeature)) s"color=$usedColor" else s"color=$unusedColor"
        builder ++= s"""${feature.toString} -> ${nextFeature.toString} [style=dotted,penwith=3,$usedStatus]\n"""
      }
      feature.requiresAll.foreachElement { requiredFeature =>
        val usedStatus = if (alreadyUsed(feature) && alreadyUsed(requiredFeature)) s"color=$usedColor" else s"color=$unusedColor"
        builder ++= s"""${requiredFeature.toString} -> ${feature.toString} [penwidth=5, $usedStatus]\n"""
      }
      feature.requiresAny.foreachElement { requiredFeature =>
        val usedStatus = if (alreadyUsed(feature) && alreadyUsed(requiredFeature)) s"color=$usedColor" else s"color=$unusedColor"
        builder ++= s"""${requiredFeature.toString} -> ${feature.toString} [$usedStatus]\n"""
      }
    }

    def subgraph(name: String, vertices: Seq[Feature]): String = {
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

    builder ++= subgraph("Filter", SubObjects.all[Feature.Category.Filter])
    builder ++= subgraph("Item.Tag", SubObjects.all[Feature.Category.Item.Tag])
    // builder ++= subgraph("Drag", SubObjects.all[Feature.Category.Drag])

    // builder ++= subgraph("Item.Task", SubObjects.all[Feature.Category.Item.Task])

    builder ++= "}\n"

    builder.result()
  }
}

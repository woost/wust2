package wust.webApp.views

import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.Icons
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{FeatureState, FocusState, GlobalState, Placeholder, TraverseState}
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, UI}

// Notes view, this is a simple view for storing note/wiki/documentation on a node.
// It  renders all direct children of noderole note and allows to add new notes.
object NotesView {

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      keyed,
      Styles.growFull,
      overflow.auto,

      cls := "notesview",
      div(
        cls := "notesview-container",

        Rx {
          val graph = GlobalState.graph()
          val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

          val childNodes = graph.noteChildrenIdx.map(nodeIdx) { childIdx =>
            graph.nodes(childIdx)
          }.sortBy(_.id)

          childNodes.map { node =>
            renderNote(node, focusState = focusState)
          }
        },
        registerDragContainer,

        inputRow(focusState)
      )
    )
  }

  private def inputRow(focusState: FocusState)(implicit ctx: Ctx.Owner) = {
    val triggerSubmit = SinkSourceHandler.publish[Unit]

    InputRow(
      Some(focusState),
      submitAction = { sub =>
        val newNode = Node.MarkdownNote(sub.text)
        val changes = GraphChanges.addNodeWithParent(newNode, ParentId(focusState.focusedId)) merge sub.changes(newNode.id)
        GlobalState.submitChanges(changes)
        FeatureState.use(Feature.CreateNoteInNotes)
      },
      submitOnEnter = false,
      showSubmitIcon = true,
      submitIcon = freeSolid.faPlus,
      placeholder = Placeholder.newNote,
      showMarkdownHelp = true,
      triggerSubmit = triggerSubmit,
    )
  }

  private def renderNote(node: Node, focusState:FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val parentId = focusState.focusedId
    val traverseState = TraverseState(focusState.focusedId)
    val nodeIdx = GlobalState.graph.map(_.idToIdxOrThrow(node.id))
    val parentIdx = GlobalState.graph.map(_.idToIdxOrThrow(parentId))

    val isExpanded = Rx {
      GlobalState.graph().isExpanded(GlobalState.userId(), node.id).getOrElse(false)
    }
    val childStats = Rx { NodeDetails.ChildStats.from(nodeIdx(), GlobalState.graph()) }

    val isDeleted = Rx {
      GlobalState.graph().isDeletedNowIdx(nodeIdx(), parentIdx())
    }

    val propertySingle = Rx {
      val graph = GlobalState.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(node.id))
    }

    val editMode = Var(false)

    div(
      cls := "ui segment",
      cls := "note",
      Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, node.id),
      Rx { VDomModifier.ifTrue(editMode())(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)") },
      div(
        cls := "notesview-note",
        cls := "enable-text-selection",

        Components.editableNode(node, editMode = editMode, config = EditableContent.Config.cancelOnError.copy(submitOnEnter = false, submitOnBlur = false)).append(
          width := "100%",
        ),
      ),
      div(
        alignItems.center,
        NodeDetails.tagsPropertiesAssignments(node.id)
      ),
      NodeDetails.cardFooter(node.id, childStats, isExpanded, focusState),
      NodeDetails.nestedTaskList(
        nodeId = node.id,
        isExpanded = isExpanded,
        focusState = focusState,
        traverseState = traverseState,
        isCompact = false,
        inOneLine = true
      ),

      Rx {
        VDomModifier.ifNot(editMode())(
          DragComponents.dragWithHandle(DragItem.Note(node.id)),
          cursor.auto, // overwrite drag cursor
        )
      },

      controls (node.id, parentId, editMode, isDeleted)
        .apply(
          position.absolute,
          top := "10px",
          right := "10px",
          Styles.flexStatic,
          marginLeft := "26px",
        ),
    )
  }

  private def controls(nodeId: NodeId, parentId: NodeId, editMode: Var[Boolean], isDeleted: Rx[Boolean])(implicit ctx: Ctx.Owner) = div(
    Styles.flex,
    alignItems.center,

    UnreadComponents.readObserver(nodeId),

    color := "#666",
    editButton(editMode),
    deleteButton(nodeId, parentId, isDeleted),
    dragHandle,
  )

  private def editButton(editMode: Var[Boolean]) = div(
    cls := "fa-fw",
    Icons.edit,
    cursor.pointer,
    margin := "5px",
    onClick.stopPropagation.foreach {
      editMode() = !editMode.now
      if (editMode.now)
        FeatureState.use(Feature.EditNote)
    }
  )

  private def deleteButton(nodeId: NodeId, parentId: NodeId, isDeleted: Rx[Boolean])(implicit ctx: Ctx.Owner) = div(
    cls := "fa-fw",
    margin := "5px",
    isDeleted.map {
      case true  => renderFontAwesomeObject(Icons.undelete)
      case false => renderFontAwesomeIcon(Icons.delete)
    },
    cursor.pointer,
    onClick.stopPropagation.foreach {
      if (isDeleted.now) {
        val changes = GraphChanges.undelete(ChildId(nodeId), ParentId(parentId))
        GlobalState.submitChanges(changes)
      } else {
        Elements.confirm("Delete this note?") {
          val changes = GraphChanges.delete(ChildId(nodeId), ParentId(parentId))
          GlobalState.submitChanges(changes)
        }
      }
      ()
    }
  )

  private def dragHandle = div(
    cls := "fa-fw",
    margin := "5px",
    cls := "draghandle",
    freeSolid.faGripVertical,
    cursor.move,
    UI.popup("bottom center") := "Drag here to drag the note"
  )
}

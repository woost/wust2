package wust.webApp.views

import fontAwesome.freeSolid
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.Icons
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{ FeatureState, FocusState, GlobalState, Placeholder }
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Elements, UI }
import monix.reactive.subjects.PublishSubject

// Notes view, this is a simple view for storing note/wiki/documentation on a node.
// It  renders all direct children of noderole note and allows to add new notes.
object NotesView {

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      keyed,
      Styles.growFull,
      overflow.auto,

      Styles.flex,
      justifyContent.center,
      div(
        padding := "20px",

        maxWidth := "980px", // like github readme
        width := "100%",

        Rx {
          val graph = GlobalState.graph()
          val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

          val childNodes = graph.childrenIdx.map(nodeIdx) { childIdx =>
            graph.nodes(childIdx)
          }.sortBy(_.id)

          childNodes.map { node =>
            VDomModifier.ifTrue(node.role == NodeRole.Note)(renderNote(node, parentId = focusState.focusedId))
          }
        },
        registerDragContainer,

        inputRow(focusState)
      )
    )
  }

  private def inputRow(focusState: FocusState)(implicit ctx: Ctx.Owner) = {
    val triggerSubmit = PublishSubject[Unit]

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
      blurAction = Some(_ => triggerSubmit.onNext(()))
    )
  }

  private def renderNote(node: Node, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val isDeleted = Rx {
      GlobalState.graph().isDeletedNow(node.id, parentId = parentId)
    }

    val propertySingle = Rx {
      val graph = GlobalState.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(node.id))
    }

    val editMode = Var(false)

    div(
      cls := "ui segment",
      cls := "note",
      div(

        // readability like github readme:
        padding := "48px",
        fontSize := "16px",
        cls := "enable-text-selection",

        Rx {
          VDomModifier.ifNot(editMode())(
            DragComponents.dragWithHandle(DragItem.Note(node.id)),
            cursor.auto, // overwrite drag cursor
          )
        },

        Components.editableNode(node, editMode = editMode, config = EditableContent.Config.cancelOnError.copy(submitOnEnter = false)).append(
          width := "100%",
        ),
      ),

      controls (node.id, parentId, editMode, isDeleted)
        .apply(
          position.absolute,
          top := "10px",
          right := "10px",
          Styles.flexStatic,
          marginLeft := "26px",
        )
    )
  }

  private def controls(nodeId: NodeId, parentId: NodeId, editMode: Var[Boolean], isDeleted: Rx[Boolean])(implicit ctx: Ctx.Owner) = div(
    Styles.flex,
    alignItems.center,

    UnreadComponents.readObserver(nodeId),

    color := "#666",
    editButton(editMode),
    zoomButton(nodeId),
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

  private def zoomButton(nodeId: NodeId) = Components.zoomButton(nodeId)(
    cls := "fa-fw",
    margin := "5px",
    onClick.stopPropagation.foreach {
      FeatureState.use(Feature.ZoomIntoNote)
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

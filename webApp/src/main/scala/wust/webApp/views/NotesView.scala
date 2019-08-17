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
import wust.webApp.state.{ FocusState, GlobalState, Placeholder, FeatureState }
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._
import wust.webUtil.Elements
import monix.reactive.subjects.PublishSubject

// Notes view, this is a simple view for storing note/wiki/documentation on a node.
// It  renders all direct children of noderole note and allows to add new notes.
object NotesView {

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    val triggerSubmit = PublishSubject[Unit]
    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",

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
    )
  }

  private def renderNote(node: Node, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val isDeleted = Rx {
      GlobalState.graph().isDeletedNow(node.id, parentId = parentId)
    }

    val editMode = Var(false)

    div(
      cls := "ui segment",
      cls := "note",
      Styles.flex,
      justifyContent.spaceBetween,
      alignItems.flexStart,

      Rx {
        VDomModifier.ifNot(editMode())(
          DragComponents.drag(DragItem.Note(node.id)),
          cursor.auto, // overwrite drag cursor
        )
      },

      editableNodeOnClick(node, editMode = editMode, config = EditableContent.Config.cancelOnError.copy(submitOnEnter = false)).apply(width := "100%"),

      div(
        Styles.flex,
        alignItems.center,

        zoomButton(node.id)(
          padding := "3px",
          marginRight := "5px",
          onClick.stopPropagation.foreach {
            FeatureState.use(Feature.ZoomIntoNote)
          }
        ),

        UnreadComponents.readObserver(node.id),

        div(
          padding := "3px",
          isDeleted.map {
            case true  => renderFontAwesomeObject(Icons.undelete)
            case false => renderFontAwesomeIcon(Icons.delete)
          },
          cursor.pointer,
          onClick.stopPropagation.foreach {
            if (isDeleted.now) {
              val changes = GraphChanges.undelete(ChildId(node.id), ParentId(parentId))
              GlobalState.submitChanges(changes)
            } else {
              Elements.confirm("Delete this note?") {
                val changes = GraphChanges.delete(ChildId(node.id), ParentId(parentId))
                GlobalState.submitChanges(changes)
              }
            }
            ()
          }
        )
      )
    )
  }
}

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
import wust.webApp.state.{FocusState, GlobalState, Placeholder}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._

// Notes view, this is a simple view for storing note/wiki/documentation on a node.
// It  renders all direct children of noderole note and allows to add new notes.
object NotesView {

  //TODO: button in each sidebar line to jump directly to view (conversation / tasks)
  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",

      Rx {
        val graph = state.graph()
        val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

        val childNodes = graph.childrenIdx.map(nodeIdx) { childIdx =>
          graph.nodes(childIdx)
        }.sortBy(_.id)

        childNodes.map { node =>
          VDomModifier.ifTrue(node.role == NodeRole.Note)(renderNote(state, node, parentId = focusState.focusedId))
        }
      },
      registerDragContainer(state),

      InputRow(
        state,
        submitAction = { sub =>
          val newNode = Node.MarkdownNote(sub.text)
          val changes = GraphChanges.addNodeWithParent(newNode, ParentId(focusState.focusedId)) merge sub.changes(newNode.id)
          state.eventProcessor.changes.onNext(changes)
        },
        submitOnEnter = false,
        showSubmitIcon = true,
        submitIcon = freeSolid.faPlus,
        placeholder = Placeholder.newNote,
        showMarkdownHelp = true
      )
    )
  }

  private def renderNote(state: GlobalState, node: Node, parentId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    val isDeleted = Rx {
      state.graph().isDeletedNow(node.id, parentId = parentId)
    }

    val editMode = Var(false)

    div(
      cls := "ui segment",
      Styles.flex,
      justifyContent.spaceBetween,
      alignItems.flexStart,

      Rx {
        VDomModifier.ifNot(editMode())(
          DragComponents.drag(DragItem.Note(node.id)),
          cursor.auto, // overwrite drag cursor
        )
      },

      editableNodeOnClick(state, node, editMode = editMode, config = EditableContent.Config.cancelOnError.copy(submitOnEnter = false)).apply(width := "100%"),

      div(
        Styles.flex,
        alignItems.center,

        zoomButton(state, node.id)( padding := "3px", marginRight := "5px"),

        Components.readObserver(state, node.id),

        div(
          padding := "3px",
          isDeleted.map {
            case true => renderFontAwesomeObject(Icons.undelete)
            case false => renderFontAwesomeIcon(Icons.delete)
          },
          cursor.pointer,
          onClick.stopPropagation.foreach {
            val changes = isDeleted.now match {
              case true => GraphChanges.undelete(ChildId(node.id), ParentId(parentId))
              case false => GraphChanges.delete(ChildId(node.id), ParentId(parentId))
            }
            state.eventProcessor.changes.onNext(changes)
            ()
          }
        )
      )
    )
  }
}

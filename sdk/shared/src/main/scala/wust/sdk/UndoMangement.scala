package wust.sdk

import wust.graph.{Node, GraphChanges}
import wust.ids._
import wust.util.collection._

object UndoManagement {
  private val changedNodes = groupByBuilder[NodeId, Node]

  case class State(prev: List[GraphChanges], current: GraphChanges, next: List[GraphChanges]) {

    def apply(action: Action): State = action match {
      case Action.Push(rawChanges) =>
        println("GOOOOO " + rawChanges)
        val changes = rawChanges.onlyUserActions
        println("DOOOO " + changes)
        if (changes.nonEmpty) {
          changes.addNodes.foreach { node =>
            changedNodes += (node.id -> node)
          }
          copy(prev = changes :: prev, current = rawChanges, next = Nil)
        } else copy(current = rawChanges)
      case Action.Undo => prev match {
        case Nil => copy(current = GraphChanges.empty)
        case recent :: remaining =>
          val reverted = recent.revert(changedNodes.result.mapValues(_.last))
          copy(prev = remaining, current = reverted, next = reverted :: next)
      }
      case Action.Redo => next match {
        case Nil => copy(current = GraphChanges.empty)
        case recent :: remaining =>
          val reverted = recent.revert(changedNodes.result.mapValues(_.last))
          copy(prev = reverted :: prev, current = reverted, next = remaining)
      }
    }

    @inline def canApply(action: Action): Boolean = action match {
      case Action.Push(_) => true
      case Action.Undo => prev.nonEmpty
      case Action.Redo => next.nonEmpty
    }
  }
  object State {
    def initial = State(Nil, GraphChanges.empty, Nil)
  }

  sealed trait Action
  object Action {
    case object Undo extends Action
    case object Redo extends Action
    case class Push(changes: GraphChanges) extends Action
  }
}

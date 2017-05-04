package wust.backend

import wust.api._

object StateTranslator {
  def applyEvent(state: State, event: ApiEvent): State = event match {
    case NewMembership(edge) if state.auth.isDefined && edge.userId == state.auth.get.user.id =>
      state.copyF(groupIds = _ ++ Set(edge.groupId))
    case _ => state
  }

  def allowsEvent(state: State, event: ApiEvent): Boolean = event match {
    case NewPost(_) => true
    case UpdatedPost(_) => true
    case NewConnection(_) => true
    case NewContainment(_) => true
    case NewOwnership(_) => true
    case NewUser(_) => true
    case NewGroup(edge) => true //TODO: for who?
    case NewMembership(edge) =>
      state.auth.map(_.user.id == edge.userId).getOrElse(false) || state.groupIds.contains(edge.groupId)
    case DeletePost(_) => true
    case DeleteConnection(_) => true
    case DeleteContainment(_) => true
    case _ => false
  }
}

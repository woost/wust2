package wust.backend

import derive.derive
import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.ids._
import wust.util.Pipe
import wust.graph._

import scala.concurrent.{ ExecutionContext, Future }

@derive(copyF)
case class State(auth: Option[JWTAuthentication], graph: Graph) {
  val user = auth.map(_.user)
  override def toString = s"State(${auth.map(_.user.name)}, posts# ${graph.posts.size})"
}
object State {
  def initial = State(auth = None, graph = Graph.empty)
}

class StateInterpreter(db: Db)(implicit ec: ExecutionContext) {
  def applyEventsToState(state: State, events: Seq[ApiEvent]): State = {
    events.foldLeft(state)((state, event) => state.copyF(graph = GraphUpdate.onEvent(_, event)))
  }

  def triggeredEvents(state: State, event: RequestEvent): Future[Seq[ApiEvent]] = Future.sequence(event.events.map {
    case NewMembership(membership) =>
      membershipEventsForState(state, membership)

    case NewGraphChanges(changes) =>
      val visibleChanges = visibleChangesForState(state, changes, event.postGroups)
      Future.successful {
        if (visibleChanges.isEmpty) Seq.empty
        else Seq(NewGraphChanges(visibleChanges))
      }

    case other =>
      println(s"####### ignored Event: $other")
      Future.successful(Nil)
  }).map(_.flatten)

  def validate(state: State): State = state.copyF(auth = _.filterNot(JWT.isExpired))

  def stateEvents(state: State)(implicit ec: ExecutionContext): Future[Seq[ApiEvent]] = {
    db.graph.getAllVisiblePosts(state.user.map(_.id))
      .map(forClient(_).consistent)
      .map(ReplaceGraph(_))
      .map { event =>
        val authEvent = state.auth
          .map(_.toAuthentication |> LoggedIn)
          .getOrElse(LoggedOut)

        Seq(authEvent, event)
      }
  }

  def stateChangeEvents(prevState: State, state: State)(implicit ec: ExecutionContext): Future[Seq[ApiEvent]] =
    (prevState.auth == state.auth) match {
      case true  => Future.successful(Seq.empty)
      case false => stateEvents(state)
    }

  private def membershipEventsForState(state: State, membership: Membership): Future[Seq[ApiEvent]] = {
    import membership._

    def currentUserInvolved = state.auth.map(_.user.id == userId).getOrElse(false)
    def ownGroupInvolved = state.graph.groupsById.isDefinedAt(groupId)
    if (currentUserInvolved) {
      // query all other members of groupId
      val groupFut = db.group.get(groupId)
      val iterableFut = db.group.members(groupId)
      val postsFut = db.group.getOwnedPosts(groupId)
      for {
        Some(group) <- groupFut
        iterable <- iterableFut
        posts <- postsFut
      } yield (for {
        (user, membership) <- iterable.toSeq
        addPosts = posts.map(forClient).toSet
        addOwnerships = posts.map(post => Ownership(post.id, membership.groupId)).toSet
        changes = Some(GraphChanges(addPosts = addPosts, addOwnerships = addOwnerships)).filterNot(_.isEmpty)
        event <- Seq(NewUser(user), NewMembership(membership)) ++ changes.map(NewGraphChanges(_))
      } yield event) :+ NewGroup(group)
    } else if (ownGroupInvolved) {
      for {
        Some(user) <- db.user.get(userId)
      } yield Seq(NewUser(user), NewMembership(membership))
      // only forward new membership and user
    } else Future.successful(Nil)
  }

  private def visibleChangesForState(state: State, changes: GraphChanges, postGroups: Map[PostId, Set[GroupId]]): GraphChanges = {
    import changes.consistent._

    val postIds = addPosts.map(_.id) ++ updatePosts.map(_.id) ++ delPosts
    val ownGroups = state.graph.groups.map(_.id).toSet
    val allowedPostIds = postIds.flatMap { postId =>
      val allowed = postGroups.get(postId).map(_ exists ownGroups).getOrElse(true)
      if (allowed) Some(postId)
      else None
    }

    changes.consistent.filter(allowedPostIds)
  }
}

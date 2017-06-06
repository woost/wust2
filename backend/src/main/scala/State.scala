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
  def onEvent(state: State, event: ApiEvent): State = {
    state.copyF(graph = GraphUpdate.onEvent(_, event))
  }

  //TODO: we should not do database queries here, this is done in each connected client
  //rather get meta information about posts in event once, then work with this information here.
  def triggeredEvents(state: State, event: ApiEvent): Future[Seq[ApiEvent]] = event match {
    case e @ (_: ReplaceGraph) => Future.successful(Seq(e))

    case e @ LoggedIn(auth) if state.auth.map(_.toAuthentication) == Some(auth) => Future.successful(Seq(e))
    case e @ LoggedOut => Future.successful(Seq(e))

    case NewMembership(membership) =>
      membershipEventsForState(state, membership)

    case NewGraphChanges(changes) =>
      visibleChangesForState(state, changes).map { visibleChanges =>
        if (visibleChanges.isEmpty) Seq.empty
        else Seq(NewGraphChanges(visibleChanges))
      }

    case other =>
      println(s"####### ignored Event: $other")
      Future.successful(Nil)
  }

  def validate(state: State): State = state.copyF(auth = _.filterNot(JWT.isExpired))

  def stateEvents(state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] = {
    Seq(
      state.auth
        .map(_.toAuthentication |> LoggedIn)
        .getOrElse(LoggedOut)
    ).map(Future.successful _) ++ Seq(
        db.graph.getAllVisiblePosts(state.user.map(_.id))
          .map(forClient(_).consistent)
          .map(ReplaceGraph(_)) //TODO: move to triggeredEvents
      )
  }

  def stateChangeEvents(prevState: State, state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] =
    (prevState.auth == state.auth) match {
      case true  => Seq.empty
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
        changes = GraphChanges(addPosts = addPosts, addOwnerships = addOwnerships)
        event <- Seq(NewUser(user), NewMembership(membership), NewGraphChanges(changes))
      } yield event) :+ NewGroup(group)
    } else if (ownGroupInvolved) {
      for {
        Some(user) <- db.user.get(userId)
      } yield Seq(NewUser(user), NewMembership(membership))
      // only forward new membership and user
    } else Future.successful(Nil)
  }

  private def visibleChangesForState(state: State, changes: GraphChanges): Future[GraphChanges] = {
    import changes.consistent._

    val postIds = addPosts.map(_.id) ++ updatePosts.map(_.id) ++ delPosts
    if (postIds.isEmpty) Future.successful(changes)
    else {
      val ownGroups = state.graph.groups.toSet
      db.ctx.transaction { implicit ec =>
        //TODO: we know the groups for addPosts already => addOwnerships
        //TODO dont query for each post? needs to be chained
        val disallowedPostIds = postIds.foldLeft(Future.successful(List.empty[PostId])) { (disallowedIds, postId) =>
          disallowedIds.flatMap { disallowedIds =>
            db.post.getGroups(postId).map { groups =>
              if (groups.isEmpty || (groups.map(forClient).toSet intersect ownGroups).nonEmpty) disallowedIds
              else postId :: disallowedIds
            }
          }
        }.map(_.toSet)

        disallowedPostIds.map(changes.consistent.withoutPosts)
      }
    }
  }
}

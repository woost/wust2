package wust.backend

import cats.syntax.group
import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.graph._
import wust.ids._

import scala.concurrent.{ExecutionContext, Future}

class StateInterpreter(jwt: JWT, db: Db)(implicit ec: ExecutionContext) {
  import ApiEvent._

  //TODO: refactor! this is difficult to reason about
  def triggeredEvents(state: State, events: List[ApiEvent]): Future[List[ApiEvent]] =
    //TODO: broken
  Future.successful(events)
    //Future.sequence(events.map {
    //case NewMembership(membership) =>
    //  membershipEventsForState(state, membership)

    //case NewUser(_) =>
    //    //TODO explicitly ignored, see membershipEventsForState: ownGroupInvolved
    //    Future.successful(Nil)

    //case NewGraphChanges(changes) =>
    //  Future.successful{
    //  val visibleChanges = visibleChangesForState(state, changes)
    //  if (visibleChanges.isEmpty) Nil
    //  else NewGraphChanges(visibleChanges) :: Nil
    //}

    //case other => Future.successful(other :: Nil)
  //}).map(_.flatten)

  private def membershipEventsForState(state: State, membership: Membership): Future[List[ApiEvent.Public]] = {
    import membership._

    def currentUserInvolved = state.auth.user.id == userId
    def ownGroupInvolved = state.graph.postsById.isDefinedAt(postId)
    if (currentUserInvolved) {
      // query all other members of groupId
      val postFut = db.post.get(postId)
      for {
        post <- postFut
      } yield for {
        event <- List(NewMembership(membership), NewGraphChanges(GraphChanges(addPosts = post.map(forClient).toSet)))
      } yield event
    } else if (ownGroupInvolved) {
      for {
        //TODO we should not need this, the newuser should be in the events already
        Some(user) <- db.user.get(userId)
      } yield List(NewUser(user), NewMembership(membership))
      // only forward new membership and user
    } else Future.successful(Nil)
  }

  private def visibleChangesForState(state: State, changes: GraphChanges): GraphChanges = {
    import changes.consistent._

    val changedPostIds = addPosts.map(_.id) ++ updatePosts.map(_.id) ++ delPosts
    val ownPosts = state.graph.postIds.toSet
    val allowedPostIds = changedPostIds intersect ownPosts
    changes.consistent.filter(allowedPostIds)
  }
}

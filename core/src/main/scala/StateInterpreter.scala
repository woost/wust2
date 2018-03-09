package wust.backend

import cats.syntax.group
import io.circe.Decoder.state
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
    Future.sequence(events.map {
    case NewMembership(membership) =>
      membershipEventsForState(state, membership)

    case NewUser(_) =>
        //TODO explicitly ignored, see membershipEventsForState: ownGroupInvolved
        Future.successful(Nil)

    case NewGraphChanges(changes) =>
      visibleChangesForState(state, changes).map {visibleChanges =>
        if (visibleChanges.isEmpty) Nil
        else NewGraphChanges(visibleChanges) :: Nil
      }

    case other => Future.successful(other :: Nil)
  }).map(_.flatten)

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

  private def visibleChangesForState(state: State, changes: GraphChanges): Future[GraphChanges] = {
    import changes.consistent._
    val affectedPostId = addPosts.map(_.id) ++ updatePosts.map(_.id) ++ delPosts
    scribe.info(s"are $affectedPostId visible to ${state.auth.user.id}?")
    db.user.visiblePosts(state.auth.user.id, affectedPostId).map {visiblePostIds =>
      scribe.info(s"visible: $visiblePostIds")
      changes.consistent.filter(visiblePostIds.toSet)
    }
  }
}

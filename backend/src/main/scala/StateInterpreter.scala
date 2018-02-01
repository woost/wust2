package wust.backend

import derive.derive
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
  def triggeredEvents(state: State, event: RequestEvent): Future[Seq[ApiEvent.Public]] = Future.sequence(event.events.map {
    case NewMembership(membership) =>
      membershipEventsForState(state, membership)

    case NewUser(_) =>
        //TODO explicitly ignored, see membershipEventsForState: ownGroupInvolved
        Future.successful(Nil)

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

  private def membershipEventsForState(state: State, membership: Membership): Future[Seq[ApiEvent.Public]] = {
    import membership._

    def currentUserInvolved = state.auth.user.id == userId
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
        //TODO we should not need this, the newuser should be in the events already
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

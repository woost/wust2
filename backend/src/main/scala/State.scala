package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.backend.auth._
import wust.db.Db
import wust.ids._
import wust.util.Pipe
import wust.graph._

import scala.concurrent.{ ExecutionContext, Future }

// TODO: crashes coverage @derive(copyF)
case class State(auth: Option[JWTAuthentication], graph: Graph) {
  val user = auth.map(_.user)
  def copyF(auth: Option[JWTAuthentication] => Option[JWTAuthentication] = identity, graph: Graph => Graph = identity) = copy(auth = auth(this.auth), graph = graph(this.graph))
}
object State {
  def initial = State(auth = None, graph = Graph.empty)
}

class StateInterpreter(db: Db)(implicit ec: ExecutionContext) {
  def onEvent(state: State, event: ApiEvent): State = {
    state.copyF(graph = GraphUpdate.onEvent(_, event))
  }

  def triggeredEvents(state: State, event: ApiEvent): Future[Seq[ApiEvent]] = event match {
    case e @ (_: NewConnection | _: NewContainment) => Future.successful(Seq(e))
    case e @ (_: DeletePost | _: DeleteConnection | _: DeleteContainment) => Future.successful(Seq(e))
    case e @ (_: ReplaceGraph) => Future.successful(Seq(e))
    case e @ (_: LoggedIn | LoggedOut) => Future.successful(Seq(e))

    case newMembership @ NewMembership(Membership(userId, groupId)) =>
      def currentUserInvolved = state.auth.map(_.user.id == userId).getOrElse(false)
      def ownGroupInvolved = state.graph.groupsById.isDefinedAt(groupId)
      if (currentUserInvolved) {
        println(s"currentUserInvolved: $userId")
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
          event <- Seq(NewUser(user), NewMembership(membership)) ++ posts.flatMap(post => Seq(NewPost(post), NewOwnership(Ownership(post.id, membership.groupId))))
        } yield event) :+ NewGroup(group)
      } else if (ownGroupInvolved) {
        for {
          Some(user) <- db.user.get(userId)
        } yield Seq(NewUser(user), newMembership)
        // only forward new membership and user
      } else Future.successful(Nil)
    case newPost @ NewPost(post) =>
      for {
        groups <- db.post.getGroups(post.id)
      } yield {
        if (groups.nonEmpty) {
          for {
            group <- (groups.map(forClient).toSet intersect state.graph.groups.toSet).toSeq
            event <- Seq(NewOwnership(Ownership(post.id, group.id)), newPost)
          } yield event
        } else Seq(newPost) // post is public
      }
    case updatedPost @ UpdatedPost(post) =>
      for {
        groups <- db.post.getGroups(post.id)
      } yield if (groups.nonEmpty) {
        for {
          group <- (groups.map(forClient).toSet intersect state.graph.groups.toSet).toSeq
          event <- Seq(updatedPost)
        } yield event
      } else Seq(updatedPost) // post is public
    case other =>
      println(s"####### ignored Event: $other")
      Future.successful(Nil)
  }

  def validate(state: State): State = state.copyF(auth = _.filterNot(JWT.isExpired))

  def stateEvents(state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] = {
    Seq(
      state.auth
        .map(_.toAuthentication |> LoggedIn)
        .getOrElse(LoggedOut)).map(Future.successful _) ++ Seq(
        db.graph.getAllVisiblePosts(state.user.map(_.id))
          .map(forClient(_).consistent)
          .map(ReplaceGraph(_)) //TODO: move to triggeredEvents
      )
  }

  def stateChangeEvents(prevState: State, state: State)(implicit ec: ExecutionContext): Seq[Future[ApiEvent]] =
    (prevState.auth == state.auth) match {
      case true => Seq.empty
      case false => stateEvents(state)
    }
}

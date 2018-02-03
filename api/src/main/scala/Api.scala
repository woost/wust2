package wust.api

import wust.graph._
import wust.ids._

trait Api[Result[_]] {
  def changeGraph(changes: List[GraphChanges]): Result[Boolean]

  def getPost(id: PostId): Result[Option[Post]]
  def getGraph(selection: Page): Result[Graph]
  def getUser(userId: UserId): Result[Option[User]]
  def addGroup(): Result[GroupId]
  def addMember(groupId: GroupId, userId: UserId): Result[Boolean]
  def addMemberByName(groupId: GroupId, userName: String): Result[Boolean]
  def getGroupInviteToken(groupId: GroupId): Result[Option[String]]
  def recreateGroupInviteToken(groupId: GroupId): Result[Option[String]]
  def acceptGroupInvite(token: String): Result[Option[GroupId]]

  def importGithubUrl(url: String): Result[Boolean]
  def importGitterUrl(url: String): Result[Boolean]
  def chooseTaskPosts(heuristic: NlpHeuristic, posts: List[PostId], num: Option[Int]): Result[List[Heuristic.ApiResult]]
}

trait AuthApi[Result[_]] {
  def assumeLogin(id: UserId): Result[Boolean]
  def register(name: String, password: String): Result[Boolean]
  def login(name: String, password: String): Result[Boolean]
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
}

sealed trait Authentication {
  def user: User
  def dbUserOpt: Option[User.Persisted] = Some(user) collect { case u: User.Persisted => u }
}
object Authentication {
  type Token = String

  case class Assumed(user: User.Assumed) extends Authentication
  object Assumed {
    def fresh = Assumed(User.Assumed(cuid.Cuid()))
  }
  case class Verified(user: User.Persisted, expires: Long, token: Token) extends Authentication {
    override def toString = s"Authentication.Verified($user)"
  }
}

sealed trait ApiError extends Any
object ApiError {
  case class ServerError(msg: String) extends AnyVal with ApiError
  case class ClientError(msg: String) extends AnyVal with ApiError
  sealed trait HandlerFailure extends Any with ApiError
  case object InternalServerError extends HandlerFailure
  case object Unauthorized extends HandlerFailure
  case object Forbidden extends HandlerFailure
}

sealed trait ApiEvent extends Any
object ApiEvent {
  sealed trait Public extends Any with ApiEvent
  sealed trait Private extends Any with ApiEvent
  sealed trait GraphContent extends Any with ApiEvent
  sealed trait AuthContent extends Any with ApiEvent

  case class NewUser(user: User) extends AnyVal with GraphContent with Public with Private
  case class NewGroup(group: Group) extends AnyVal with GraphContent with Public with Private
  case class NewMembership(membership: Membership) extends AnyVal with GraphContent with Public with Private
  case class NewGraphChanges(changes: GraphChanges) extends AnyVal with GraphContent with Public {
    override def toString = s"NewGraphChanges(#changes: ${changes.size})"
  }
  case class LoggedIn(auth: Authentication.Verified) extends AnyVal with AuthContent with Private
  case class AssumeLoggedIn(auth: Authentication.Assumed) extends AnyVal with AuthContent with Private
  case class ReplaceGraph(graph: Graph) extends AnyVal with GraphContent with Private {
    override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
  }

  def separateByScope(events: Seq[ApiEvent]): (List[Private], List[Public]) =
    events.foldRight((List.empty[Private], List.empty[Public])) { case (ev, (privs, pubs)) =>
      val newPrivs = ev match {
        case ev: Private => ev :: privs
        case _ => privs
      }
      val newPubs = ev match {
        case ev: Public => ev :: pubs
        case _ => pubs
      }
      (newPrivs, newPubs)
    }

  def separateByContent(events: Seq[ApiEvent]): (List[GraphContent], List[AuthContent]) =
    events.foldRight((List.empty[GraphContent], List.empty[AuthContent])) {
      case (ev: GraphContent, (gs, as)) => (ev :: gs, as)
      case (ev: AuthContent, (gs, as)) => (gs, ev :: as)
    }
}

object Heuristic {
  case class PostResult(measure: Option[Double], posts: List[Post])
  case class IdResult(measure: Option[Double], postIds: List[PostId])

  type Result = PostResult
  type ApiResult = IdResult
}

sealed trait NlpHeuristic extends Any
object NlpHeuristic {
    case class DiceSorensen(nGram: Int) extends AnyVal with NlpHeuristic
    case object Hamming extends NlpHeuristic
    case class Jaccard(nGram: Int) extends AnyVal with NlpHeuristic
    case object Jaro extends NlpHeuristic
    case object JaroWinkler extends NlpHeuristic
    case object Levenshtein extends NlpHeuristic
    case class NGram(nGram: Int) extends AnyVal with NlpHeuristic
    case class Overlap(nGram: Int) extends AnyVal with NlpHeuristic
    case object RatcliffObershelp extends NlpHeuristic
    case class WeightedLevenshtein(delWeight: Int, insWeight: Int, subWeight: Int) extends NlpHeuristic
}

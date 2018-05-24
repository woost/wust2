package wust.api

import sloth.PathName
import wust.graph._
import wust.ids._
import cats.data.NonEmptyList

trait Api[Result[_]] {
  def changeGraph(changes: List[GraphChanges]): Result[Boolean]
  @PathName("changeGraphSingle")
  def changeGraph(changes: GraphChanges): Result[Boolean] = changeGraph(changes :: Nil)
  @PathName("changeGraphOnBehalf")
  def changeGraph(changes: List[GraphChanges], onBehalf: Authentication.Token): Result[Boolean]

  def getPost(id: PostId): Result[Option[Post]]
  def getGraph(selection: Page): Result[Graph]
  def getUser(userId: UserId): Result[Option[User]]
  def addMember(postId: PostId, userId: UserId, accessLevel: AccessLevel): Result[Boolean]
  def setJoinDate(postId: PostId, joinDate: JoinDate): Result[Boolean]
//  def addMemberByName(postId: PostId, userName: String): Result[Boolean]

  def importGithubUrl(url: String): Result[Boolean]
  def importGitterUrl(url: String): Result[Boolean]
  def chooseTaskPosts(heuristic: NlpHeuristic, posts: List[PostId], num: Option[Int]): Result[List[Heuristic.ApiResult]]

  def log(message: String): Result[Boolean]
}

@PathName("Push")
trait PushApi[Result[_]] {
  def subscribeWebPush(subscription: WebPushSubscription): Result[Boolean]
  def getPublicKey(): Result[Option[String]]
}

@PathName("Auth")
trait AuthApi[Result[_]] {
  def assumeLogin(user: User.Assumed): Result[Boolean]
  def register(name: String, password: String): Result[Boolean]
  def login(name: String, password: String): Result[Boolean]
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
  def verifyToken(token: Authentication.Token): Result[Option[Authentication.Verified]]
  def issuePluginToken(): Result[Authentication.Verified]
}

sealed trait Authentication {
  def user: User
  def dbUserOpt: Option[User.Persisted] = Some(user) collect { case u: User.Persisted => u }
}
object Authentication {
  type Token = String

  case class Assumed(user: User.Assumed) extends Authentication
  object Assumed {
    def fresh = Assumed(User.Assumed(UserId.fresh, PostId.fresh))
  }
  case class Verified(user: User.Persisted, expires: Long, token: Token) extends Authentication {
    override def toString = s"Authentication.Verified($user)"
  }
}

sealed trait ApiError extends Any
object ApiError {
  case class ServerError(msg: String) extends AnyVal with ApiError
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
  case class NewMembership(membership: Membership) extends AnyVal with GraphContent with Public with Private
  object NewMembership {
    def apply(userId:UserId, postId:PostId):NewMembership = new NewMembership(Membership(userId, postId))
  }

  sealed trait NewGraphChanges extends GraphContent {
    val changes: GraphChanges
  }
  object NewGraphChanges {
    def unapply(event: ApiEvent): Option[GraphChanges] = event match {
      case gc: NewGraphChanges => Some(gc.changes)
      case _ => None
    }

    def apply(changes: GraphChanges) = ForPublic(changes)
    case class ForPublic(changes: GraphChanges) extends NewGraphChanges with Public
    case class ForPrivate(changes: GraphChanges) extends NewGraphChanges with Private
    case class ForAll(changes: GraphChanges) extends NewGraphChanges with Public with Private
  }

  case class ReplaceGraph(graph: Graph) extends AnyVal with GraphContent with Private {
    override def toString = s"ReplaceGraph(#posts: ${graph.posts.size})"
  }

  case class LoggedIn(auth: Authentication.Verified) extends AnyVal with AuthContent with Private
  case class AssumeLoggedIn(auth: Authentication.Assumed) extends AnyVal with AuthContent with Private

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

case class WebPushSubscription(endpointUrl: String, p256dh: String, auth: String)

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

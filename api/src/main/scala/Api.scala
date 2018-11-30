package wust.api

import sloth.PathName
import wust.graph._
import wust.ids.{EdgeData, _}
import cats.data.NonEmptyList
import wust.graph.Node.User

trait Api[Result[_]] {
  def changeGraph(changes: List[GraphChanges]): Result[Boolean]
  @PathName("changeGraphSingle")
  def changeGraph(changes: GraphChanges): Result[Boolean] = changeGraph(changes :: Nil)
  @PathName("changeGraphOnBehalf")
  def changeGraph(changes: List[GraphChanges], onBehalf: Authentication.Token): Result[Boolean]

  def getGraph(selection: Page): Result[Graph]
  def addMember(nodeId: NodeId, userId: UserId, accessLevel: AccessLevel): Result[Boolean]

  def getNode(nodeId: NodeId): Result[Option[Node]]
  @PathName("getNodeOnBehalf")
  def getNode(nodeId: NodeId, onBehalf: Authentication.Token): Result[Option[Node]]
//  def addMemberByName(nodeId: NodeId, userName: String): Result[Boolean]
  def getUserId(name: String): Result[Option[UserId]]

//  def importGithubUrl(url: String): Result[Boolean]
//  def importGitterUrl(url: String): Result[Boolean]
  def chooseTaskNodes(
      heuristic: NlpHeuristic,
      nodes: List[NodeId],
      num: Option[Int]
  ): Result[List[Heuristic.ApiResult]]

  def currentTime:Result[EpochMilli]

  //TODO have methods for warn/error. maybe a LogApi trait?
  def log(message: String): Result[Boolean]
}

@PathName("Push")
trait PushApi[Result[_]] {
  def subscribeWebPush(subscription: WebPushSubscription): Result[Boolean]
  def cancelSubscription(subscription: WebPushSubscription): Result[Boolean]
  def getPublicKey(): Result[Option[String]]
}

@PathName("Auth")
trait AuthApi[Result[_]] {
  def changePassword(password: String): Result[Boolean]
  def assumeLogin(user: AuthUser.Assumed): Result[Boolean]
  def register(name: String, email: String, password: String): Result[AuthResult]
  def login(name: String, password: String): Result[AuthResult]
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
  def verifyToken(token: Authentication.Token): Result[Option[Authentication.Verified]]
  def issuePluginToken(): Result[Authentication.Verified]
  def createImplicitUserForApp(): Result[Option[Authentication.Verified]]

  def getUserDetail(id: UserId): Result[Option[UserDetail]]
  def updateUserEmail(id: UserId, newEmail: String): Result[Boolean]
  def resendEmailVerification(id: UserId): Result[Unit]
}

case class UserDetail(
  userId: UserId,
  email: Option[String],
  verified: Boolean
)

sealed trait AuthResult
object AuthResult {
  case object BadUser extends AuthResult
  case object BadEmail extends AuthResult
  case object BadPassword extends AuthResult
  case object Success extends AuthResult
}

sealed trait AuthUser {
  def id: UserId
  def name: String
  def toNode: Node.User
}
object AuthUser {
  sealed trait Persisted extends AuthUser
  case class Real(id: UserId, name: String, revision: Int)
      extends Persisted {
    def toNode =
      Node.User(id, NodeData.User(name, isImplicit = false, revision), NodeMeta.User)
  }
  case class Implicit(id: UserId, name: String, revision: Int)
      extends Persisted {
    def toNode =
      Node.User(id, NodeData.User(name, isImplicit = true, revision), NodeMeta.User)
  }
  case class Assumed(id: UserId) extends AuthUser {
    def name = s"unregistered-user-${id.toCuidString.takeRight(4)}"
    def toNode = Node.User(id, NodeData.User(name, isImplicit = true, revision = 0), NodeMeta.User)
  }

  implicit def AsUserInfo(user: AuthUser): UserInfo =
    UserInfo(user.id, user.name)
}

sealed trait Authentication {
  def user: AuthUser
  def dbUserOpt: Option[AuthUser.Persisted] = Some(user) collect { case u: AuthUser.Persisted => u }
}
object Authentication {
  type Token = String

  case class Assumed(user: AuthUser.Assumed) extends Authentication
  object Assumed {
    def fresh = Assumed(AuthUser.Assumed(UserId.fresh))
  }
  case class Verified(user: AuthUser.Persisted, expires: Long, token: Token) extends Authentication {
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

  sealed trait NewGraphChanges extends GraphContent {
    val changes: GraphChanges
    val user: User
  }
  object NewGraphChanges {
    def unapply(event: ApiEvent): Option[(User, GraphChanges)] = event match {
      case gc: NewGraphChanges => Some(gc.user -> gc.changes)
      case _                   => None
    }

    def apply(user: User, changes: GraphChanges) = ForPublic(user, changes)
    case class ForPublic(user: User, changes: GraphChanges) extends NewGraphChanges with Public
    case class ForPrivate(user: User, changes: GraphChanges) extends NewGraphChanges with Private
    case class ForAll(user: User, changes: GraphChanges) extends NewGraphChanges with Public with Private
  }

  case class ReplaceGraph(graph: Graph) extends AnyVal with GraphContent with Private {
    override def toString = s"ReplaceGraph(#nodes: ${graph.nodes.size})"
  }

  case class LoggedIn(auth: Authentication.Verified) extends AnyVal with AuthContent with Private
  case class AssumeLoggedIn(auth: Authentication.Assumed)
      extends AnyVal
      with AuthContent
      with Private

  def separateByScope(events: Seq[ApiEvent]): (List[Private], List[Public]) =
    events.foldRight((List.empty[Private], List.empty[Public])) {
      case (ev, (privs, pubs)) =>
        val newPrivs = ev match {
          case ev: Private => ev :: privs
          case _           => privs
        }
        val newPubs = ev match {
          case ev: Public => ev :: pubs
          case _          => pubs
        }
        (newPrivs, newPubs)
    }

  def separateByContent(events: Seq[ApiEvent]): (List[GraphContent], List[AuthContent]) =
    events.foldRight((List.empty[GraphContent], List.empty[AuthContent])) {
      case (ev: GraphContent, (gs, as)) => (ev :: gs, as)
      case (ev: AuthContent, (gs, as))  => (gs, ev :: as)
    }
}

case class WebPushSubscription(endpointUrl: String, p256dh: String, auth: String)

object Heuristic {
  case class PostResult(measure: Option[Double], nodes: List[Node.Content])
  case class IdResult(measure: Option[Double], nodeIds: List[NodeId])

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
  case class WeightedLevenshtein(delWeight: Int, insWeight: Int, subWeight: Int)
      extends NlpHeuristic
}

package wust.api

import sloth.PathName
import wust.graph._
import wust.ids.{EdgeData, _}
import cats.data.NonEmptyList

trait Api[Result[_]] {
  def changeGraph(changes: List[GraphChanges]): Result[Boolean]
  @PathName("changeGraphSingle")
  def changeGraph(changes: GraphChanges): Result[Boolean] = changeGraph(changes :: Nil)
  @PathName("changeGraphOnBehalf")
  def changeGraph(changes: List[GraphChanges], onBehalf: Authentication.Token): Result[Boolean]

  def getGraph(selection: Page): Result[Graph]
  def addMember(nodeId: NodeId, userId: UserId, accessLevel: AccessLevel): Result[Boolean]
//  def addMemberByName(nodeId: NodeId, userName: String): Result[Boolean]

//  def importGithubUrl(url: String): Result[Boolean]
//  def importGitterUrl(url: String): Result[Boolean]
  def chooseTaskNodes(
      heuristic: NlpHeuristic,
      nodes: List[NodeId],
      num: Option[Int]
  ): Result[List[Heuristic.ApiResult]]

  def currentTime:Result[EpochMilli] // TODO: bug: API calls without parameters is not possible

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
  def assumeLogin(user: AuthUser.Assumed): Result[Boolean]
  def register(name: String, password: String): Result[AuthResult]
  def login(name: String, password: String): Result[AuthResult]
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
  def verifyToken(token: Authentication.Token): Result[Option[Authentication.Verified]]
  def issuePluginToken(): Result[Authentication.Verified]
}

sealed trait AuthResult
object AuthResult {
  case object BadUser extends AuthResult
  case object BadPassword extends AuthResult
  case object Success extends AuthResult
}

//TODO: anyval those hierarchies!
sealed trait AuthUser {
  def id: UserId
  def name: String
  def channelNodeId: NodeId
}
object AuthUser {
  sealed trait Persisted extends AuthUser {
    def toNode: Node.User
  }
  case class Real(id: UserId, name: String, revision: Int, channelNodeId: NodeId)
      extends Persisted {
    def toNode =
      Node.User(id, NodeData.User(name, isImplicit = false, revision, channelNodeId), NodeMeta.User)
  }
  case class Implicit(id: UserId, name: String, revision: Int, channelNodeId: NodeId)
      extends Persisted {
    def toNode =
      Node.User(id, NodeData.User(name, isImplicit = true, revision, channelNodeId), NodeMeta.User)
  }
  case class Assumed(id: UserId, channelNodeId: NodeId) extends AuthUser {
    def name = s"anon-${id.toCuidString.takeRight(4)}"
  }

  implicit def AsUserInfo(user: AuthUser): UserInfo =
    UserInfo(user.id, user.name, user.channelNodeId)
}

sealed trait Authentication {
  def user: AuthUser
  def dbUserOpt: Option[AuthUser.Persisted] = Some(user) collect { case u: AuthUser.Persisted => u }
}
object Authentication {
  type Token = String

  case class Assumed(user: AuthUser.Assumed) extends Authentication
  object Assumed {
    def fresh = Assumed(AuthUser.Assumed(UserId.fresh, NodeId.fresh))
  }
  case class Verified(user: AuthUser.Persisted, expires: Long, token: Token)
      extends Authentication {
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
  }
  object NewGraphChanges {
    def unapply(event: ApiEvent): Option[GraphChanges] = event match {
      case gc: NewGraphChanges => Some(gc.changes)
      case _                   => None
    }

    def apply(changes: GraphChanges) = ForPublic(changes)
    case class ForPublic(changes: GraphChanges) extends NewGraphChanges with Public
    case class ForPrivate(changes: GraphChanges) extends NewGraphChanges with Private
    case class ForAll(changes: GraphChanges) extends NewGraphChanges with Public with Private
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

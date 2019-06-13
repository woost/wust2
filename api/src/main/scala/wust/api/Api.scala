package wust.api

import sloth.PathName
import wust.graph.Node.User
import wust.graph._
import wust.ids._

trait Api[Result[_]] {
  def changeGraph(changes: List[GraphChanges]): Result[Boolean]
  @PathName("changeGraphSingle")
  def changeGraph(changes: GraphChanges): Result[Boolean] = changeGraph(changes :: Nil)
  @PathName("changeGraphOnBehalf")
  def changeGraph(changes: List[GraphChanges], onBehalf: Authentication.Token): Result[Boolean]

  def fileDownloadBaseUrl: Result[Option[StaticFileUrl]]
  def fileUploadConfiguration(key: String, fileSize: Int, fileName: String, fileContentType: String): Result[FileUploadConfiguration]
  def deleteFileUpload(key: String): Result[Boolean]
  def getUploadedFiles: Result[Seq[UploadedFile]]

  def getGraph(selection: Page): Result[Graph]

  def getUnreadChannels(): Result[List[NodeId]]

  // simple api
  def getNodeList(parentId: Option[NodeId], nodeRole: Option[NodeRole] = None): Result[List[SimpleNode]]

  def getNode(nodeId: NodeId): Result[Option[Node]]
  @PathName("getNodeOnBehalf")
  def getNode(nodeId: NodeId, onBehalf: Authentication.Token): Result[Option[Node]]
  def getUserByEMail(email: String): Result[Option[Node.User]]

//  def importGithubUrl(url: String): Result[Boolean]
//  def importGitterUrl(url: String): Result[Boolean]
  def currentTime:Result[EpochMilli]

  //TODO have methods for warn/error. maybe a LogApi trait?
  def log(message: String): Result[Boolean]

  //TODO covenant currently does not allow us to extract the useragent header from a websocket request, so we need to provide it here explicitly.
  def feedback(clientInfo: ClientInfo, message: String): Result[Unit]
}

@PathName("Push")
trait PushApi[Result[_]] {
  def subscribeWebPush(subscription: WebPushSubscription): Result[Boolean]
  def cancelSubscription(subscription: WebPushSubscription): Result[Boolean]
  def getPublicKey(): Result[Option[String]]
}

@PathName("Auth")
trait AuthApi[Result[_]] {
  def changePassword(password: Password): Result[Boolean]
  def assumeLogin(user: AuthUser.Assumed): Result[Boolean]
  def register(name: String, email: String, password: Password): Result[AuthResult]
  def login(email: String, password: Password): Result[AuthResult]
  def loginReturnToken(email: String, password: Password): Result[Option[Authentication.Verified]] //TODO: provide separate public api (json) instead
  def loginToken(token: Authentication.Token): Result[Boolean]
  def logout(): Result[Boolean]
  def verifyToken(token: Authentication.Token): Result[Option[Authentication.Verified]]
  def issuePluginToken(): Result[Authentication.Verified]
  def createImplicitUserForApp(): Result[Option[Authentication.Verified]]
  def acceptInvitation(token: Authentication.Token): Result[Unit]

  def getUserDetail(id: UserId): Result[Option[UserDetail]]
  def updateUserEmail(id: UserId, newEmail: String): Result[Boolean]
  def resendEmailVerification(id: UserId): Result[Unit]
  def invitePerMail(address: String, nodeId:NodeId): Result[Unit]
}

case class Password(string: String) extends AnyVal {
  override def toString = "Password(***)"
}

case class ClientInfo(userAgent: String)

case class UserDetail(
  userId: UserId,
  email: Option[String],
  verified: Boolean
)

sealed trait AuthResult
object AuthResult {
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
  sealed trait Persisted extends AuthUser {
    def updateName(name: String): AuthUser.Persisted
  }
  case class Real(id: UserId, name: String, revision: Int) extends Persisted {
    def toNode = Node.User(id, NodeData.User(name, isImplicit = false, revision), NodeMeta.User)
    override def toString = s"Real(${id.toBase58} ${id.toUuid}, $name, $revision)"
    def updateName(name: String) = copy(name = name)
  }
  case class Implicit(id: UserId, name: String, revision: Int) extends Persisted {
    def toNode = Node.User(id, NodeData.User(name, isImplicit = true, revision), NodeMeta.User)
    override def toString = s"Implicit(${id.toBase58} ${id.toUuid}, $name, $revision)"
    def updateName(name: String) = copy(name = name)
  }
  case class Assumed(id: UserId) extends AuthUser {
    def name = ""
    def toNode = Node.User(id, NodeData.User(name, isImplicit = true, revision = 0), NodeMeta.User)
    override def toString = s"Assumed(${id.toBase58} ${id.toUuid})"
  }

  implicit def AsUserInfo(user: AuthUser): UserInfo =
    UserInfo(user.id, user.name)
}

sealed trait Authentication {
  def user: AuthUser
  def dbUserOpt: Option[AuthUser.Persisted] = Some(user) collect { case u: AuthUser.Persisted => u }
}
object Authentication {
  case class Token(string: String) extends AnyVal {
    override def toString = "Token(***)"
  }

  case class Assumed(user: AuthUser.Assumed) extends Authentication
  object Assumed {
    def fresh = Assumed(AuthUser.Assumed(UserId.fresh))
  }
  case class Verified(user: AuthUser.Persisted, expires: Long, token: Token) extends Authentication
}

sealed trait ApiError
object ApiError {
  case object IncompatibleApi extends ApiError
  case object InternalServerError extends ApiError
  case object Unauthorized extends ApiError
  case object Forbidden extends ApiError
}

sealed trait ApiEvent {
  def scope: ApiEvent.Scope
}
object ApiEvent {
  sealed trait Scope
  object Scope {
    case object Public extends Scope
    case object Private extends Scope
    case object All extends Scope
  }

  sealed trait GraphContent extends ApiEvent
  sealed trait AuthContent extends ApiEvent {
    def scope = Scope.Private
  }

  case class NewGraphChanges(user: User, changes: GraphChanges, scope: ApiEvent.Scope) extends GraphContent
  object NewGraphChanges {
    def unapply(event: ApiEvent): Option[(User, GraphChanges)] = event match {
      case gc: NewGraphChanges => Some(gc.user -> gc.changes)
      case _                   => None
    }

    def forPublic(user: User, changes: GraphChanges) = new NewGraphChanges(user, changes, Scope.Public)
    def forPrivate(user: User, changes: GraphChanges) = new NewGraphChanges(user, changes, Scope.Private)
    def forAll(user: User, changes: GraphChanges) = new NewGraphChanges(user, changes, Scope.All)
  }

  case class ReplaceGraph(graph: Graph) extends GraphContent {
    def scope = Scope.Private
    override def toString = s"ReplaceGraph(#nodes: ${graph.nodes.size})"
  }

  case class ReplaceNode(oldNodeId: NodeId, newNode: Node) extends GraphContent {
    def scope = Scope.Public
  }

  case class LoggedIn(auth: Authentication.Verified) extends AuthContent
  case class AssumeLoggedIn(auth: Authentication.Assumed) extends AuthContent

  def separateToPrivateAndPublicEvents(events: Seq[ApiEvent]): (List[ApiEvent], List[ApiEvent]) = {
    val privs = List.newBuilder[ApiEvent]
    val pubs = List.newBuilder[ApiEvent]
    events.foreach { event =>
      event.scope match {
        case Scope.Private =>
          privs += event
        case Scope.Public =>
          pubs += event
        case Scope.All =>
          privs += event
          pubs += event
      }
    }

    (privs.result(), pubs.result())
  }

  def separateToGraphAndAuthContent(events: Seq[ApiEvent]): (List[GraphContent], List[AuthContent]) = {
    val graphs = List.newBuilder[GraphContent]
    val auths = List.newBuilder[AuthContent]
    events.foreach {
      case ev: GraphContent => graphs += ev
      case ev: AuthContent  => auths += ev
    }

    (graphs.result(), auths.result())
  }
}

sealed trait FileUploadConfiguration
object FileUploadConfiguration {
  case class UploadToken(baseUrl: String, credential: String, policyBase64: String, signature: String, validSeconds: Int, acl: String, key: String, algorithm: String, date: String, contentDisposition: String, cacheControl: String) extends FileUploadConfiguration
  case class KeyExists(key: String) extends FileUploadConfiguration
  case object QuotaExceeded extends FileUploadConfiguration
  case object ServiceUnavailable extends FileUploadConfiguration

  val maxUploadBytesPerFile = 50 * 1024 * 1024 // 50 mb
  val maxUploadBytesPerUser = 500 * 1024 * 1024 // 500 mb
  val cacheMaxAgeSeconds = 365 * 24 * 60 * 60 // 1 year
}
case class StaticFileUrl(url: String)
case class UploadedFile(nodeId: NodeId, size: Long, file: NodeData.File)

case class WebPushSubscription(endpointUrl: String, p256dh: String, auth: String)

object Heuristic {
  case class PostResult(measure: Option[Double], nodes: List[Node.Content])
  case class IdResult(measure: Option[Double], nodeIds: List[NodeId])

  type Result = PostResult
  type ApiResult = IdResult
}


// Simple Api
case class SimpleNode(id:NodeId, content: String, role: NodeRole)
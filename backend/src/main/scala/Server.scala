package wust.backend

import java.io.{PrintWriter, StringWriter}

import akka.http.scaladsl.server.Directives._
import boopickle.Default._
import wust.api._
import wust.backend.auth._
import wust.framework._
import wust.util.Pipe

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

case class UserError(error: ApiError) extends Exception

case class State(auth: Option[JWTAuthentication])

class ApiRequestHandler(dispatcher: EventDispatcher) extends RequestHandler[ApiEvent, ApiError, State] {
  private val enableImplicitAuth: Boolean = true //TODO config
  private def createImplicitAuth(): Future[Option[JWTAuthentication]] = {
    if (enableImplicitAuth) Db.user.createImplicitUser().map(JWT.generateAuthentication).map(Option.apply)
    else Future.successful(None)
  }

  private def subscribeChannels(auth: Option[JWTAuthentication], groups: Seq[UserGroup], sender: EventSender[ApiEvent]) = {
    dispatcher.unsubscribe(sender)

    dispatcher.subscribe(sender, Channel.Graph)
    dispatcher.subscribe(sender, Channel.UserGroup(Db.UserGroup.default.id))
    auth.foreach { auth =>
      dispatcher.subscribe(sender, Channel.User(auth.user.id))
      groups
          .map(g => Channel.UserGroup(g.id))
          .foreach(dispatcher.subscribe(sender, _))
    }
  }

  private def onStateChange(sender: EventSender[ApiEvent], state: State) = {
    //TODO: with current graphselection
    val newGraph = Db.graph.getAllVisiblePosts(state.auth.map(_.user.id))
    val newGroups = state.auth
      .map(auth => Db.user.allGroups(auth.user.id))
      .getOrElse(Future.successful(Seq.empty))

    import sender.send
    newGroups.foreach { groups =>
      subscribeChannels(state.auth, groups, sender)
      state.auth
        .filter(_.user.isImplicit)
        .foreach(auth => ImplicitLogin(auth.toAuthentication) |> send)
      ReplaceUserGroups(groups) |> send
      newGraph.foreach { graph =>
        ReplaceGraph(graph) |> sender.send
      }
    }
  }

  override def router(sender: EventSender[ApiEvent], state: Future[State]) = {
    val apiAuth = new AuthenticatedAccess(state.map(_.auth), createImplicitAuth, UserError(Unauthorized))

    (
      AutowireServer.route[Api](new ApiImpl(apiAuth)) orElse
        AutowireServer.route[AuthApi](new AuthApiImpl(apiAuth))) andThen {
      res =>
        val newState = for {
          state <- state
          auth <- apiAuth.createdOrActualAuth
        } yield if (state.auth != auth) {
          val newState = state.copy(auth = auth)
          onStateChange(sender, newState)
          newState
        } else state

        RequestResult(newState, res)
    }
  }

  override val initialState = Future.successful(State(None))
  override def onClientStop(sender: EventSender[ApiEvent], state: State) = dispatcher.unsubscribe(sender)

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override val toError: PartialFunction[Throwable, ApiError] = {
    case UserError(error) => error
    case NonFatal(e) =>
      val sw = new StringWriter
      e.printStackTrace(new PrintWriter(sw))
      scribe.error("request handler threw exception:\n" + sw.toString)
      InternalServerError
  }
}

object Server {
  private val dispatcher = new ChannelEventBus
  private val ws = new WebsocketServer[ApiEvent, ApiError, State](new ApiRequestHandler(dispatcher))

  private val route = (path("ws") & get) {
    ws.websocketHandler
  } ~ (path("health") & get) {
    complete("ok")
  }

  def emit(ev: ChannelEvent) = {
    //optimiziation to serialize event only once
    scribe.info(s"serializing event: $ev")
    val serialized = ws.serializedEvent(ev.event)
    dispatcher.publish(SerializedChannelEvent(ev.channel, serialized))
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}

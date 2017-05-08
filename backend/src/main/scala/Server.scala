package wust.backend

import java.io.{ PrintWriter, StringWriter }

import akka.http.scaladsl.server.Directives._
import boopickle.Default._
import wust.api._
import wust.backend.auth._
import wust.framework._
import wust.util.Pipe
import wust.db
import wust.backend.config.Config
import wust.backend.dbConversions._
import wust.graph.Group

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import collection.breakOut

// TODO: crashes coverage @derive(copyF)
case class State(auth: Option[JWTAuthentication]) {
  def copyF(auth: Option[JWTAuthentication] => Option[JWTAuthentication] = identity) = copy(auth = auth(this.auth))
}

class ApiRequestHandler(dispatcher: EventDispatcher) extends RequestHandler[ApiEvent, ApiError, State] {
  private def subscribeChannels(auth: Option[JWTAuthentication], extraGroups: Iterable[Group], sender: EventSender[ApiEvent]) = {
    dispatcher.unsubscribe(sender)

    dispatcher.subscribe(sender, Channel.All)
    //TODO: currently updates to a group (via api) are not automatically subscribed!
    extraGroups
      .map(g => Channel.Group(g.id))
      .foreach(dispatcher.subscribe(sender, _))

    auth.foreach { auth =>
      dispatcher.subscribe(sender, Channel.User(auth.user.id))
    }
  }

  private def onStateChange(sender: EventSender[ApiEvent], prevState: Option[State], state: State) = {
    if (prevState.map(_.auth != state.auth).getOrElse(true)) {
      val userIdOpt = state.auth.map(_.user.id)
      //TODO: with current graphselection
      val newGraph = db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent)

      import sender.send
      newGraph.onComplete {
        case Success(graph) =>
          subscribeChannels(state.auth, graph.groups, sender)
          state.auth
            .filter(_.user.isImplicit)
            .foreach(auth => ImplicitLogin(auth.toAuthentication) |> send)
          ReplaceGraph(graph) |> send
        case Failure(t) =>
          scribe.error(s"failed to get initial graph for state: $state")
          scribe.error(t)
      }
    }
  }

  override def router(sender: EventSender[ApiEvent], state: Future[State]) = {
    val stateAccess = StateAccess(state)

    (
      AutowireServer.route[Api](new ApiImpl(stateAccess)) orElse
      AutowireServer.route[AuthApi](new AuthApiImpl(stateAccess))) andThen {
        res =>
          val newState = for {
            state <- state
            newState <- stateAccess.state
          } yield if (state != newState) {
            onStateChange(sender, Option(state), newState)
            newState
          } else state

          RequestResult(newState, res)
      }
  }

  override def onClientStart(sender: EventSender[ApiEvent]) = {
    val state = State(None)
    scribe.info(s"client started: $state")
    onStateChange(sender, None, state)
    Future.successful(state)
  }

  override def onClientStop(sender: EventSender[ApiEvent], state: State) = {
    scribe.info(s"client stopped: $state")
    dispatcher.unsubscribe(sender)
  }

  override def pathNotFound(path: Seq[String]): ApiError = NotFound(path)
  override val toError: PartialFunction[Throwable, ApiError] = {
    case ApiException(error) => error
    case NonFatal(e) =>
      scribe.error("request handler threw exception")
      scribe.error(e)
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

  def emitDynamic(ev: ApiEvent with DynamicEvent): Unit = {
    val channel = ev match {
      //TODO: this is sent to every client, but we need to filter.
      // two problems:
      //  - who is allowed to see the event (ownership/group)?
      //  - who is interested in this specific graph event? which graph is visible in the client?
      // maybe needs multiple channels for multiple groups?
      // => then how to make batch publish on dispatcher in order to not send events multiple times
      // to the same client. (if he is in more than one of corresponding Groups)
      case _ => Channel.All
    }

    emit(ChannelEvent(channel, ev))
  }

  def emit(ev: ChannelEvent): Unit = {
    // dispatcher.publish(ev)
    //optimiziation to serialize event only once
    scribe.info(s"serializing event: $ev")
    val serialized = ws.serializedEvent(ev.event)
    dispatcher.publish(SerializedChannelEvent(ev.channel, serialized))
  }

  def run(port: Int) = ws.run(route, "0.0.0.0", port)
}

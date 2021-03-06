package wust.sdk

import monix.execution.{ Ack, Scheduler }
import monix.reactive.Observable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.subjects.{ PublishSubject, PublishToOneSubject }
import wust.api.ApiEvent._
import wust.api._
import wust.graph._
import wust.ids.UserId

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

sealed trait SyncStatus
object SyncStatus {
  case object Sending extends SyncStatus
  case object Pending extends SyncStatus
  case object Done extends SyncStatus
  case object Error extends SyncStatus
}

object EventProcessor {

  //TODO factory and constructor shared responsibility
  def apply(
    eventStream: Observable[Seq[ApiEvent]],
    enrichChanges: (GraphChanges, UserId, Graph) => GraphChanges,
    sendChange: List[GraphChanges] => Future[Boolean],
    initialAuth: Authentication,
    analyticsTrackError: (String,String) => Unit,
  )(implicit scheduler: Scheduler): EventProcessor = {
    val s = eventStream.share
    val graphEvents = s
      .map(_.collect { case e: ApiEvent.GraphContent => e })
      .collect { case l if l.nonEmpty => l }

    val authEvents = s
      .map(_.collect { case e: ApiEvent.AuthContent => e })
      .collect { case l if l.nonEmpty => l }

    new EventProcessor(
      graphEvents,
      authEvents,
      enrichChanges,
      sendChange,
      initialAuth,
      analyticsTrackError,
    )
  }

  sealed trait AppliedChangeResult
  object AppliedChangeResult {
    case object Success extends AppliedChangeResult
    case object Rejected extends AppliedChangeResult
    case object TryLater extends AppliedChangeResult
  }

  sealed trait SendChangeResult
  object SendChangeResult {
    case object Success extends SendChangeResult
    sealed trait Failure extends SendChangeResult
    case class Error(error:ApiError) extends Failure
    case class UnexpectedError(error:Throwable) extends Failure
    case object Rejected extends Failure
  }
}

import EventProcessor.AppliedChangeResult
import EventProcessor.SendChangeResult

class EventProcessor private (
  eventStream: Observable[Seq[ApiEvent.GraphContent]],
  authEventStream: Observable[Seq[ApiEvent.AuthContent]],
  enrichChanges: (GraphChanges, UserId, Graph) => GraphChanges,
  sendChange: List[GraphChanges] => Future[Boolean],
  val initialAuth: Authentication,
  analyticsTrackError: (String,String) => Unit,
)(implicit scheduler: Scheduler) {
  // import Client.storage
  // storage.graphChanges <-- localChanges //TODO

  val stopEventProcessing = PublishSubject[Boolean]

  private val forbiddenChangesSubject = PublishSubject[Seq[GraphChanges]]
  val forbiddenChanges: Observable[Seq[GraphChanges]] = forbiddenChangesSubject

  private val currentAuthUpdate = PublishSubject[Authentication]
  val currentAuth: Observable[Authentication] = Observable(currentAuthUpdate, authEventStream.collect {
    case events if events.nonEmpty => EventUpdate.createAuthFromEvent(events.last)
  }).merge.share
  val currentUser: Observable[AuthUser] = currentAuth.map(_.user)

  //TODO: publish only Observer? publishtoone subject? because used as hot observable?
  val changes = PublishSubject[GraphChanges]
  val changesRemoteOnly = PublishSubject[GraphChanges]
  val changesWithoutEnrich = PublishSubject[GraphChanges]
  val localEvents = PublishSubject[ApiEvent.GraphContent]
  localEvents.foreach { e =>
    scribe.debug("EventProcessor.localEvents: " + e)
  }

  // public reader
  val (localChanges, localChangesRemoteOnly, graph): (Observable[GraphChanges], Observable[GraphChanges], Observable[Graph]) = {
    val rawGraph = PublishSubject[Graph]()
    var lastGraph = Graph.empty
    var lastUser: AuthUser = initialAuth.user

    def enrichedChangesf(changes: Observable[GraphChanges], doExternalEnrich: Boolean = true) = changes.map { changes =>
      val graph = lastGraph
      val user = lastUser
      val newChanges = if (doExternalEnrich) enrichChanges(changes, user.id, graph).consistent.withAuthor(user.id) else changes
      scribe.info(s"Local Graphchanges: ${newChanges.toPrettyString(graph)}")
      NewGraphChanges.forPrivate(user.toNode, newChanges)
    }.filter(_.changes.nonEmpty)

    val localChanges = enrichedChangesf(changes).share
    val localChangesRemoteOnly = enrichedChangesf(changesRemoteOnly).share
    val localChangesWithoutEnrich = enrichedChangesf(changesWithoutEnrich, doExternalEnrich = false).share

    val graphEvents = BufferWhenTrue(Observable(
      eventStream,
      localEvents.map(Seq(_)),
      localChanges.map(Seq(_)),
      localChangesWithoutEnrich.map(Seq(_))
    ).merge, stopEventProcessing)

    currentUser.subscribe(
      { user =>
        lastUser = user
        Ack.Continue
      },
      { error =>
          scribe.error("Error while processing current user", error)
          analyticsTrackError("Error while processing current user", error.getMessage())
      }
    )
    graphEvents.subscribe(
      { events =>
        scribe.debug("EventProcessor scan: " + lastGraph + ", " + events)
        val localEvents = LocalGraphUpdateEvent.deconstruct(lastGraph, events)
        val newGraph = localEvents match {
          case LocalGraphUpdateEvent.NewGraph(graph) =>
            //TODO just to be sure if stopEventProcessing was not realiably reset.
            // we force set stopEventProcessing to false when a new graph arrives
            stopEventProcessing.onNext(false)

            graph

          case LocalGraphUpdateEvent.NewChanges(changes) => lastGraph applyChanges changes
        }
        lastGraph = newGraph
        rawGraph.onNext(newGraph)
      },
      { error =>
        scribe.error("Error while processing graph events", error)
        analyticsTrackError("Error while processing graph events", error.getMessage())
      }
    )

    (
      Observable(localChanges, localChangesWithoutEnrich).merge.map(_.changes),
      localChangesRemoteOnly.map(_.changes),
      rawGraph
    )
  }

  // whenever the user changes something himself, we want to open up event processing again
  localChanges.map(_ => false).subscribe(stopEventProcessing)

  def applyChanges(changes: GraphChanges)(implicit scheduler: Scheduler): Future[Graph] = {
    //TODO: this function is not perfectly correct. A change could be written into rawGraph, before the current change is applied
    //TODO should by sync
    val obs = graph.headL
    this.changes.onNext(changes)
    obs.runToFuture
  }

  case class ChangesIndex(changes: GraphChanges, index: Long)
  private val localChangesIndexed: Observable[ChangesIndex] =
    Observable(localChanges, localChangesRemoteOnly).merge
      .zipWithIndex.map{ case (c, i) => ChangesIndex(c, i) }
      .asyncBoundary(Unbounded)

  case class SendChangesStatus(changes: GraphChanges, index: Long, result: AppliedChangeResult)
  val sendingChanges: Observable[SendChangesStatus] = {
    Observable.tailRecM(localChangesIndexed.map(_ -> 0).delayOnNext(500 millis)) { changes =>
      changes.flatMap { case (c, retries) =>
        val maxRetries = 3
        @inline def success = Right(SendChangesStatus(c.changes, c.index, result = AppliedChangeResult.Success))
        @inline def rejected = Right(SendChangesStatus(c.changes, c.index, result = AppliedChangeResult.Rejected))
        @inline def tryLater = Right(SendChangesStatus(c.changes, c.index, result = AppliedChangeResult.TryLater))
        @inline def retry = {
          if (retries >= maxRetries) rejected //TODO: tryLater?
          else Left(Observable(c -> (retries + 1)).sample(Math.pow(3, (retries + 1)) seconds))
        }

        Observable.fromFuture(sendChangesAndLog(c.changes :: Nil)).map {
          case SendChangeResult.Success => success
          case SendChangeResult.Rejected => rejected
          case SendChangeResult.Error(error) => error match {
            case ApiError.IncompatibleApi => tryLater
            case ApiError.InternalServerError => retry
            case ApiError.Unauthorized => rejected // TODO: tryLater?
            case ApiError.Forbidden => rejected
          }
          case SendChangeResult.UnexpectedError(t) => retry
        }
      }
    }.share
  }

  sendingChanges.subscribe(
    _ => Ack.Continue,
    {
      err =>
        scribe.error("[Events] Error sending out changes, cannot continue", err) //TODO this is a critical error and should never happen? need something to notify the application of an unresolvable problem.
        analyticsTrackError("Error sending out changes", "should never happen, cannot continue")
    }
  )

  graph.withLatestFrom(currentAuth)((_, _)).subscribe(
    {
      case (graph, auth @ Authentication.Verified(user: AuthUser.Persisted, _, _)) =>
        graph.nodesById(user.id).asInstanceOf[Option[Node.User]].fold[Future[Ack]](Ack.Continue) { userNode =>
          val newName = userNode.data.name
          val newImageFile = userNode.data.imageFile
          if (newName != user.name || newImageFile != user.imageFile) currentAuthUpdate.onNext(auth.copy(user = user.update(name = newName, imageFile = newImageFile)))
          else Ack.Continue
        }
      case _ => Ack.Continue
    }: ((Graph, Authentication)) => Future[Ack]
  )

  val changesInTransit: Observable[List[GraphChanges]] = localChangesIndexed
    .map(c => (c.changes, c.index))
    .combineLatest[Long](sendingChanges.map(_.index).prepend(-1L))
    .scan(List.empty[(GraphChanges, Long)]) {
      case (prevList, (nextLocal, sentIdx)) =>
        (prevList :+ nextLocal) collect { case t @ (_, idx) if idx > sentIdx => t }
    }
    .map(_.map(_._1))
    .share

  private def logSendChangeResult(changes: Seq[GraphChanges], result: SendChangeResult): Unit = result match {
    case SendChangeResult.Success => ()
    case SendChangeResult.Error(error) => error match {
      case ApiError.Forbidden =>
        scribe.warn(s"ChangeGraph request forbidden, will ignore changes: $changes")
        analyticsTrackError("Changes Forbidden", "forbidden")

        forbiddenChangesSubject.onNext(changes)
        //TODO: we should prompt user for errors and notify him of problem. for now just accept, these changes will be lost.
        //TODO: stop processing, prompt for reload. case ApiError.IncompatibleApi =>

      case error =>
        scribe.warn(s"ChangeGraph request with error respoonse '${error}': $changes")
        analyticsTrackError("ChangeGraph request with error respoonse", s"'${error}': $changes")
    }
    case SendChangeResult.UnexpectedError(t) =>

      scribe.warn(s"ChangeGraph request failed '${t}': $changes")
      analyticsTrackError("ChangeGraph request failed", s"'${t}': $changes")
    case SendChangeResult.Rejected =>
      scribe.warn(s"ChangeGraph request returned false: $changes")
      analyticsTrackError("ChangeGraph request returned false", s"$changes")
  }

  private def sendChangesRecovered(changes: Seq[GraphChanges]): Future[SendChangeResult] = {
    sendChange(changes.toList).transform {
      case Success(true) => Success(SendChangeResult.Success)
      case Success(false) => Success(SendChangeResult.Rejected)
      case Failure(t) =>
        t match {
          case e: covenant.ws.WsClient.ErrorException[ApiError @unchecked] =>
            Success(SendChangeResult.Error(e.error))
          case e =>
            Success(SendChangeResult.UnexpectedError(e))
        }
    }
  }

  private def sendChangesAndLog(changes: Seq[GraphChanges]): Future[SendChangeResult] = {
    sendChangesRecovered(changes).map { r =>
      logSendChangeResult(changes, r)
      r
    }
  }
}

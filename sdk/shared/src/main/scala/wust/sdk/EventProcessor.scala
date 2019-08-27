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
    initialAuth: Authentication
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
      initialAuth
    )
  }
}

class EventProcessor private (
  eventStream: Observable[Seq[ApiEvent.GraphContent]],
  authEventStream: Observable[Seq[ApiEvent.AuthContent]],
  enrichChanges: (GraphChanges, UserId, Graph) => GraphChanges,
  sendChange: List[GraphChanges] => Future[Boolean],
  val initialAuth: Authentication
)(implicit scheduler: Scheduler) {
  // import Client.storage
  // storage.graphChanges <-- localChanges //TODO

  val stopEventProcessing = PublishSubject[Boolean]

  private val currentAuthUpdate = PublishSubject[Authentication]
  val currentAuth: Observable[Authentication] = Observable(currentAuthUpdate, authEventStream.collect {
    case events if events.nonEmpty => EventUpdate.createAuthFromEvent(events.last)
  }).merge.share
  val currentUser: Observable[AuthUser] = currentAuth.map(_.user)

  //TODO: publish only Observer? publishtoone subject? because used as hot observable?
  val changes = PublishSubject[GraphChanges]
  val changesRemoteOnly = PublishSubject[GraphChanges]
  val localEvents = PublishSubject[ApiEvent.GraphContent]
  localEvents.foreach { e =>
    scribe.debug("EventProcessor.localEvents: " + e)
  }

  // public reader
  val (localChanges, localChangesRemoteOnly, graphEvents, graph): (Observable[GraphChanges], Observable[GraphChanges], Observable[LocalGraphUpdateEvent], Observable[Graph]) = {
    val rawGraph = PublishToOneSubject[Graph]()
    val sharedRawGraph = rawGraph.share //TODO: when we get rid of enrichment, move to GlobalState

    def enrichedChangesf(changes: Observable[GraphChanges]) = changes.withLatestFrom2(currentUser.prepend(initialAuth.user), sharedRawGraph.prepend(Graph.empty)) { (changes, user, graph) =>
      val newChanges = enrichChanges(changes, user.id, graph)
      scribe.info(s"Local Graphchanges: ${newChanges.toPrettyString(graph)}")
      newChanges
    }

    val enrichedChanges = enrichedChangesf(changes)
    val enrichedChangesRemoteOnly = enrichedChangesf(changesRemoteOnly)

    def localChangesf(changes: Observable[GraphChanges]): Observable[(GraphChanges, AuthUser)] = changes.withLatestFrom(currentUser.prepend(initialAuth.user))((g, u) => (g, u)).collect {
      case (changes, user) if changes.nonEmpty => (changes.consistent.withAuthor(user.id), user)
    }.share

    val localChanges = localChangesf(enrichedChanges)
    val localChangesRemoteOnly = localChangesf(enrichedChangesRemoteOnly)

    val localChangesAsEvents = localChanges.map { case (changes, user) => Seq(NewGraphChanges.forPrivate(user.toNode, changes)) }
    val graphEvents = BufferWhenTrue(Observable(eventStream, localEvents.map(Seq(_)), localChangesAsEvents).merge, stopEventProcessing)
      .scan((Graph.empty, LocalGraphUpdateEvent.empty)) { (lastGraphWithLastEvents, events) =>
        scribe.debug("EventProcessor scan: " + lastGraphWithLastEvents + ", " + events)
        val (lastGraph, _) = lastGraphWithLastEvents
        val localEvents = LocalGraphUpdateEvent.deconstruct(lastGraph, events)
        val newGraph = localEvents match {
          case LocalGraphUpdateEvent.NewGraph(graph)     =>
            //TODO just to be sure if stopEventProcessing was not realiably reset.
            // we force set stopEventProcessing to false when a new graph arrives
            stopEventProcessing.onNext(false)

            graph

          case LocalGraphUpdateEvent.NewChanges(changes) => lastGraph applyChanges changes
        }
        (newGraph, localEvents)
      }.share

    graphEvents.map(_._1) subscribe rawGraph

    (
      localChanges.map(_._1),
      localChangesRemoteOnly.map(_._1),
      graphEvents.map(_._2),
      sharedRawGraph
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

  private val localChangesIndexed: Observable[(GraphChanges, Long)] = Observable(localChanges, localChangesRemoteOnly).merge.zipWithIndex.asyncBoundary(Unbounded)

  private val sendingChanges: Observable[Long] = {
    val localChangesIndexedBusy = localChangesIndexed.bufferIntrospective(maxSize = 10)
      .map(list => (list.map(_._1), list.last._2))

    Observable.tailRecM(localChangesIndexedBusy.delayOnNext(500 millis)) { changes =>
      changes.flatMap {
        case (c, idx) =>
          Observable.fromFuture(sendChanges(c)).map {
            case true =>
              Right(idx)
            case false =>
              // TODO delay with exponential backoff
              Left(Observable((c, idx)).sample(5 seconds))
          }
      }
    }.share
  }

  sendingChanges.subscribe(
    _ => Ack.Continue,
    err => scribe.error("[Events] Error sending out changes, cannot continue", err) //TODO this is a critical error and should never happen? need something to notify the application of an unresolvable problem.
  )

  graph.withLatestFrom(currentAuth)((_, _)).subscribe(
    {
      case (graph, auth @ Authentication.Verified(user: AuthUser.Persisted, _, _)) =>
        graph.nodesById(user.id).asInstanceOf[Option[Node.User]].fold[Future[Ack]](Ack.Continue) { userNode =>
          val newName = userNode.data.name
          if (newName != user.name) currentAuthUpdate.onNext(auth.copy(user = user.updateName(name = newName)))
          else Ack.Continue
        }
      case _ => Ack.Continue
    }: ((Graph, Authentication)) => Future[Ack]
  )

  val changesInTransit: Observable[List[GraphChanges]] = localChangesIndexed
    .combineLatest[Long](sendingChanges.startWith(Seq(-1)))
    .scan(List.empty[(GraphChanges, Long)]) {
      case (prevList, (nextLocal, sentIdx)) =>
        (prevList :+ nextLocal) collect { case t @ (_, idx) if idx > sentIdx => t }
    }
    .map(_.map(_._1))
    .share

  private def sendChanges(changes: Seq[GraphChanges]): Future[Boolean] = {
    //TODO: why is import wust.util._ not enough to resolve RichFuture?
    // We only need it for the 2.12 polyfill
    new wust.util.RichFuture(sendChange(changes.toList)).transform {
      case Success(success) =>
        if (!success) {
          scribe.warn(s"ChangeGraph request returned false: $changes")
        }

        Success(success)
      case Failure(t) =>
        scribe.warn(s"ChangeGraph request failed '${t}': $changes")

        Success(false)
    }
  }
}

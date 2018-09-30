package wust.sdk

import monix.execution.Scheduler
import monix.reactive.{Observable, OverflowStrategy}
import monix.reactive.subjects.{PublishSubject, PublishToOneSubject}
import wust.api.ApiEvent._
import wust.api._
import wust.ids.NodeId
import wust.graph._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import concurrent.Promise
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack
import wust.ids.EdgeData

import scala.util.control.NonFatal
import scala.concurrent.duration._
import scala.collection.breakOut

sealed trait SyncStatus
object SyncStatus {
  case object Sending extends SyncStatus
  case object Pending extends SyncStatus
  case object Done extends SyncStatus
  case object Error extends SyncStatus
}

object EventProcessor {

  //TODO factory and constructor shared responibility
  def apply(
      eventStream: Observable[Seq[ApiEvent]],
      enrichChanges: (GraphChanges, Graph) => GraphChanges,
      sendChange: List[GraphChanges] => Future[Boolean],
      initialAuth: Authentication
  )(implicit scheduler: Scheduler): EventProcessor = {
    val s = eventStream.share
    val graphEvents = s
      .map(_.collect { case e: ApiEvent.GraphContent => e })
      .collect { case l if l.nonEmpty => l }
      .share

    val authEvents = s
      .map(_.collect { case e: ApiEvent.AuthContent => e })
      .collect { case l if l.nonEmpty => l }
      .share

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
    enrichChanges: (GraphChanges, Graph) => GraphChanges,
    sendChange: List[GraphChanges] => Future[Boolean],
    val initialAuth: Authentication
)(implicit scheduler: Scheduler) {
  // import Client.storage
  // storage.graphChanges <-- localChanges //TODO

  val currentAuth: Observable[Authentication] = authEventStream.collect {
    case events if events.nonEmpty => EventUpdate.createAuthFromEvent(events.last)
  }
  val currentUser: Observable[AuthUser] = currentAuth.map(_.user)

  // changes that are only applied to the graph but are never sent
  val nonSendingChanges = PublishSubject[GraphChanges] // TODO: merge with manualUnsafeEvents?

  //TODO: publish only Observer? publishtoone subject? because used as hot observable?
  val changes = PublishSubject[GraphChanges]
  object enriched {
    val changes = PublishSubject[GraphChanges]
  }

  // public reader
  val (localChanges, graph): (Observable[GraphChanges], Observable[Graph]) = {
    // events  withLatestFrom
    // --------O----------------> localchanges
    //         ^          |
    //         |          v
    //         -----------O---->--
    //          graph,viewconfig

    val rawGraph = PublishToOneSubject[Graph]()
    val sharedRawGraph = rawGraph.share
    val rawGraphWithInit = sharedRawGraph.startWith(Seq(Graph.empty))

    val enrichedChanges = enriched.changes.withLatestFrom(rawGraphWithInit)(enrichChanges)
    val allChanges = Observable.merge(enrichedChanges, changes)

    val localChanges: Observable[GraphChanges] =
      allChanges.withLatestFrom2(currentUser.startWith(Seq(initialAuth.user)), rawGraphWithInit)((a, b, g) => (a, b, g)).collect {
        case (changes, user, graph) if changes.nonEmpty =>
          scribe.info("[Events] Got raw local changes:")
          GraphChanges.log(changes, Some(graph))

          val changesCandidate = changes.consistent.withAuthor(user.id)

          // Workaround - quick fix: This prevents that changing a nodes content breaks ordering
          val authorEdgesToRemove = for {
            changedNode <- changesCandidate.addNodes.collect{case Node.Content(id, _, _) if graph.nodeIds.contains(id) => id}
            authorEdge <- changesCandidate.addEdges.collect{case e @ Edge.Author(userId, EdgeData.Author(_), nodeId) if userId == user.id && nodeId == changedNode => e}
            graphAuthorEdge <- graph.edges.collect{case e @ Edge.Author(userId, EdgeData.Author(_), nodeId) if userId == user.id && nodeId == changedNode => e}
          } yield (authorEdge, graphAuthorEdge)

          changesCandidate.copy(addEdges = changesCandidate.addEdges -- authorEdgesToRemove.map(_._1) ++ authorEdgesToRemove.map(_._2))
      }.share

    val localEvents = Observable.merge(localChanges, nonSendingChanges).withLatestFrom(currentUser)((g, u) => (g, u)).map(gc => Seq(NewGraphChanges(gc._2.id, gc._1)))
    val graphEvents = Observable.merge(eventStream, localEvents)

    val graphWithChanges: Observable[Graph] = graphEvents.scan(Graph.empty) { (graph, events) =>
      val newGraph = events.foldLeft(graph)(EventUpdate.applyEventOnGraph)
      scribe.info("[Events] Got new graph: " + newGraph)
      newGraph
    }

    graphWithChanges subscribe rawGraph

    (localChanges, sharedRawGraph)
  }

  def applyChanges(changes: GraphChanges)(implicit scheduler: Scheduler): Future[Graph] = {
    //TODO: this function is not perfectly correct. A change could be written into rawGraph, before the current change is applied
    //TODO should by sync
    val obs = graph.headL
    this.changes.onNext(changes)
    obs.runAsync
  }

  private val localChangesIndexed: Observable[(GraphChanges, Long)] =
    localChanges
      .zipWithIndex
      .asyncBoundary(OverflowStrategy.Unbounded)

  private val sendingChanges: Observable[Long] = Observable
    .tailRecM(localChangesIndexed) { changes =>
      changes.flatMap {
        case (c, idx) =>
          Observable.fromFuture(sendChanges(c :: Nil)).map {
            case true =>
              scribe.info(s"Successfully sent out changes from EventProcessor")
              Right(idx)
            case false =>
              // TODO delay with exponential backoff
              // TODO: take more from buffer if fails?
              Left(Observable((c, idx)).sample(1 seconds))
          }
      }
    }
    .share

  sendingChanges.subscribe(
    { c =>
      println("[Events] Sending out changes done: " + c)
      Ack.Continue
    },
    err => scribe.error("[Events] Error sending out changes, cannot continue", err)
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

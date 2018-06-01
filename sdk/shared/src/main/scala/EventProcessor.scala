package wust.sdk

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import wust.api.ApiEvent._
import wust.api._
import wust.graph._
import wust.util.BufferWhenTrue

import scala.concurrent.Future
import scala.util.{Failure, Success}
import concurrent.Promise
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.{PublishSubject}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import scala.util.control.NonFatal
import scala.concurrent.duration._

sealed trait SyncStatus
object SyncStatus {
  case object Sending extends SyncStatus
  case object Pending extends SyncStatus
  case object Done extends SyncStatus
  case object Error extends SyncStatus
}

sealed trait SyncMode
object SyncMode {
  case object Live extends SyncMode
  case object Local extends SyncMode

  val default = Live
  val all = Seq(Live, Local)
}

case class ChangesHistory(undos: List[GraphChanges], redos: List[GraphChanges], current: GraphChanges) {
  def canUndo = undos.nonEmpty
  def canRedo = redos.nonEmpty
  def undo(graph: Graph) = undos match {
    case changes :: undos => ChangesHistory(undos = undos, redos = changes :: redos, current = changes.revert(graph.postsById)) //TODO
    case Nil => copy(current = GraphChanges.empty)
  }
  def redo = redos match {
    case changes :: redos => ChangesHistory(undos = changes :: undos, redos = redos, current = changes)
    case Nil => copy(current = GraphChanges.empty)
  }
  def push(changes: GraphChanges) = copy(undos = changes :: undos, redos = Nil, current = changes)

  def apply(graph: Graph): ChangesHistory.Action => ChangesHistory = {
    case ChangesHistory.NewChanges(changes) => push(changes)
    case ChangesHistory.Undo => undo(graph)
    case ChangesHistory.Redo => redo
    case ChangesHistory.Clear => ChangesHistory.empty
  }
}
object ChangesHistory {
  def empty = ChangesHistory(Nil, Nil, GraphChanges.empty)

  sealed trait Action extends Any
  case class NewChanges(changes: GraphChanges) extends AnyVal with Action
  sealed trait UserAction extends Action
  case object Undo extends UserAction
  case object Redo extends UserAction
  case object Clear extends UserAction
}

object EventProcessor {

  //TODO factory and constructor shared responibility
  def apply(
             rawEventStream: Observable[Seq[ApiEvent]],
             syncDisabled: Observable[Boolean],
             enrich: (GraphChanges, Graph) => GraphChanges,
             sendChange: List[GraphChanges] => Future[Boolean]
           )(implicit scheduler: Scheduler): EventProcessor = {
    val eventStream: Observable[Seq[ApiEvent]] = {
      // when sync is disabled, holding back incoming graph events
      val partitionedEvents:Observable[(Seq[ApiEvent], Seq[ApiEvent])] = rawEventStream.map(_.partition {
        case NewGraphChanges(_) => true
        case _ => false
      })

      val graphEvents = partitionedEvents.map(_._1)
      val otherEvents = partitionedEvents.map(_._2)
      //TODO bufferunless here will crash merge for rawGraph with infinite loop.
      // somehow `x.bufferUnless(..) merge y.bufferUnless(..)` does not work.
      val bufferedGraphEvents = BufferWhenTrue(graphEvents, syncDisabled).map(_.flatten)

      Observable.merge(bufferedGraphEvents, otherEvents)
    }

    val graphEvents = eventStream
      .map(_.collect { case e: ApiEvent.GraphContent => e })
      .collect { case l if l.nonEmpty => l }

    val authEvents = eventStream
      .map(_.collect { case e: ApiEvent.AuthContent => e })
      .collect { case l if l.nonEmpty => l }

    new EventProcessor(graphEvents, authEvents, syncDisabled, enrich, sendChange)
  }
}

class EventProcessor private(
                              eventStream: Observable[Seq[ApiEvent.GraphContent]],
                              authEventStream: Observable[Seq[ApiEvent.AuthContent]],
                              syncDisabled: Observable[Boolean],
                              enrich: (GraphChanges, Graph) => GraphChanges,
                              sendChange: List[GraphChanges] => Future[Boolean]
                            )(implicit scheduler: Scheduler) {
  // import Client.storage
  // storage.graphChanges <-- localChanges //TODO

  val currentAuth: Observable[Authentication] = authEventStream.collect {
    case events if events.nonEmpty => EventUpdate.createAuthFromEvent(events.last)
  }

  // changes that are only applied to the graph but are never sent
  val nonSendingChanges = PublishSubject[GraphChanges] // TODO: merge with manualUnsafeEvents?

  //TODO: publish only Observer?
  val changes = PublishSubject[GraphChanges]
  object enriched {
    val changes = PublishSubject[GraphChanges]
  }
  object history {
    val action = PublishSubject[ChangesHistory.UserAction]
  }

  val unsafeManualEvents = PublishSubject[ApiEvent.GraphContent] // TODO: this is a hack

  // public reader
  val (changesHistory: Observable[ChangesHistory], localChanges: Observable[GraphChanges], rawGraph: Observable[Graph]) = {

    // events  withLatestFrom
    // --------O----------------> localchanges
    //         ^          |
    //         |          v
    //         -----------O---->--
    //          graph,viewconfig

    val rawGraph = PublishSubject[Graph]()

    changes.foreach { c => println("[Events] Got local changes: " + c) }
    enriched.changes.foreach { c => println("[Events] Got enriched local changes: " + c) }

    val enrichedChanges = enriched.changes.withLatestFrom(rawGraph)(enrich)
    val allChanges = Observable.merge(enrichedChanges, changes)
    val rawLocalChanges = allChanges.collect { case changes if changes.nonEmpty => changes.consistent }

    allChanges.foreach { c => println("[Events] Got all local changes: " + c) }

    val changesHistory = Observable.merge(rawLocalChanges.map(ChangesHistory.NewChanges), history.action)
      .withLatestFrom(rawGraph)((action, graph) => (action, graph))
      .scan(ChangesHistory.empty) {
        case (history, (action, rawGraph)) => history(rawGraph)(action)
      }
    val localChanges = changesHistory.collect { case history if history.current.nonEmpty => history.current }

    localChanges.foreach { c => println("[Events] Got local changes after history: " + c) }

    val localChangesWithNonSending = Observable.merge(localChanges, nonSendingChanges)
    val localEvents = localChangesWithNonSending.map(c => Seq(NewGraphChanges(c)))
    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = Observable.merge(eventStream, localEvents, unsafeManualEvents.map(Seq(_)))

    val graphWithChanges: Observable[Graph] = graphEvents.scan(Graph.empty) { (graph, events) => events.foldLeft(graph)(EventUpdate.applyEventOnGraph) }

    graphWithChanges subscribe rawGraph

    (changesHistory, localChanges, rawGraph.map(_.consistent))
  }

  def applyChanges(changes:GraphChanges):Future[Graph] = {
    //TODO: this function is not perfectly correct. A change could be written into rawGraph, before the current change is applied
    //TODO should by sync
    this.changes.onNext(changes)
    val appliedToGraph = Promise[Graph]
    val obs = rawGraph.take(1)
    obs.foreach(appliedToGraph.success)
    obs.doOnError(appliedToGraph.failure) // das compiled
    appliedToGraph.future
  }

  private val localChangesIndexed: Observable[(GraphChanges, Long)] = localChanges.zipWithIndex
  // TODO: NonEmptyList in observables
  private val bufferedChanges: Observable[(Seq[GraphChanges], Long)] =
    BufferWhenTrue(localChangesIndexed, syncDisabled).map(l => l.map(_._1) -> l.last._2)

  bufferedChanges.foreach { c => println("[Events] Got local changes buffered: " + c) }

  private val sendingChanges: Observable[Long] = Observable.tailRecM(bufferedChanges) { changes =>
    changes.flatMap { case (c, idx) =>
      Observable.fromFuture(sendChanges(c)).map {
        case true =>
          scribe.info(s"Successfully sent out changes from EventProcessor")
          Right(idx)
        case false =>
          // TODO delay with exponential backoff
          // TODO: take more from buffer if fails?
          Left(Observable((c, idx)).sample(1 seconds))
      }
    }
  }.share

  sendingChanges.foreach { c => println("[Events] Sending out changes done: " + c) }

  val changesInTransit: Observable[List[GraphChanges]] = localChangesIndexed
    .combineLatest[Long](sendingChanges.startWith(Seq(-1)))
    .scan(List.empty[(GraphChanges, Long)]) { case (prevList, (nextLocal, sentIdx)) =>
      (prevList :+ nextLocal) collect { case t@(_, idx) if idx > sentIdx => t }
    }
    .map(_.map(_._1))
    .startWith(Seq(Nil))

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

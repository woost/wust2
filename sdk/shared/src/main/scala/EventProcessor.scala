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
  def undo = undos match {
    case changes :: undos => ChangesHistory(undos = undos, redos = changes :: redos, current = changes.revert(Map.empty)) //TODO
    case Nil => copy(current = GraphChanges.empty)
  }
  def redo = redos match {
    case changes :: redos => ChangesHistory(undos = changes :: undos, redos = redos, current = changes)
    case Nil => copy(current = GraphChanges.empty)
  }
  def push(changes: GraphChanges) = copy(undos = changes :: undos, redos = Nil, current = changes)
}
object ChangesHistory {
  def empty = ChangesHistory(Nil, Nil, GraphChanges.empty)

  sealed trait Action extends Any
  case class NewChanges(changes: GraphChanges) extends AnyVal with Action
  sealed trait UserAction extends Action
  case object Undo extends UserAction
  case object Redo extends UserAction
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

    val enrichedChanges = enriched.changes.withLatestFrom(rawGraph.startWith(Seq(Graph.empty)))(enrich)
    val allChanges = Observable.merge(enrichedChanges, changes)
    val rawLocalChanges = allChanges.collect { case changes if changes.nonEmpty => changes.consistent }

    val changesHistory = Observable.merge(rawLocalChanges.map(ChangesHistory.NewChanges), history.action).scan(ChangesHistory.empty) {
      case (history, ChangesHistory.NewChanges(changes)) => history.push(changes)
      case (history, ChangesHistory.Undo) => history.undo
      case (history, ChangesHistory.Redo) => history.redo
    }

    val localChanges = changesHistory.collect { case history if history.current.nonEmpty => history.current }

    val localEvents = localChanges.map(c => Seq(NewGraphChanges(c)))
    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = Observable.merge(eventStream, localEvents, unsafeManualEvents.map(Seq(_)))

    val graphWithChanges: Observable[Graph] = graphEvents.scan(Graph.empty) { (graph, events) => events.foldLeft(graph)(EventUpdate.applyEventOnGraph) }

    graphWithChanges subscribe rawGraph

    (changesHistory, localChanges, rawGraph)
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

  private val localChangesIndexed = localChanges.zipWithIndex
  // TODO: NonEmptyList in observables
  private val bufferedChanges: Observable[(Seq[GraphChanges], Long)] =
    BufferWhenTrue(localChangesIndexed, syncDisabled).map(l => l.map(_._1) -> l.last._2)

  private val sendingChanges: Observable[Long] = Observable.tailRecM(bufferedChanges) { changes =>
    changes.flatMap { case (c, idx) =>
      Observable.fromFuture(sendChanges(c)).map {
        case true =>
          scribe.info(s"Sent out changes: $c")
          Right(idx)
        case false =>
          // TODO delay with exponential backoff
          // TODO: take more from buffer if fails?
          Left(Observable((c, idx)).sample(1 seconds))
      }
    }
  }.share

  val areChangesSynced: Observable[Boolean] = {
    val lastLocalIndex = localChangesIndexed.map(_._2).startWith(Seq(-1))
    val lastSendIndex = sendingChanges.startWith(Seq(-1))
    lastLocalIndex.combineLatest(lastSendIndex).map { case (l,s) => l == s }
  }

  // private val hasError = createHandler[Boolean](false)
  // private val localChanges = Var(storage.graphChanges)
  // private val changesInTransit = Var(List.empty[GraphChanges])

  // private var undoHistory: List[GraphChanges] = Nil
  // private var redoHistory: List[GraphChanges] = Nil
  // private val deletedPostsById = new mutable.HashMap[PostId, Post]

  // def currentChanges = (changesInTransit.now ++ localChanges.now).foldLeft(GraphChanges.empty)(_ merge _)

  //TODO better
  // val canUndo = createHandler[Boolean](undoHistory.nonEmpty)
  // val canRedo = createHandler[Boolean](redoHistory.nonEmpty)

  // val status: Rx[SyncStatus] = Rx {
  //   if (changesInTransit().nonEmpty) SyncStatus.Sending
  //   else if (hasError()) SyncStatus.Error
  //   else if (!localChanges().isEmpty) SyncStatus.Pending
  //   else SyncStatus.Done
  // }

  //TODO this writes to localstorage then needed, once for localchanges changes and once for changesintransit changes
  // Rx {
  //   storage.graphChanges = changesInTransit() ++ localChanges()
  // }

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
  // def flush()(implicit ec: ExecutionContext): Unit = if (changesInTransit.now.isEmpty) {
  //   val newChanges = localChanges.now
  //   state.syncMode.now match {
  //     case _ if newChanges.isEmpty => ()
  //     case SyncMode.Live =>
  //       Var.set(
  //         VarTuple(localChanges, Nil),
  //         VarTuple(changesInTransit, newChanges),
  //         VarTuple(hasError, false)
  //       )
  //       println(s"persisting localChanges: $newChanges")
  //       Client.api.changeGraph(newChanges).call().onComplete {
  //         case Success(true) =>
  //           changesInTransit() = Nil

  //           val compactChanges = newChanges.foldLeft(GraphChanges.empty)(_ merge _)
  //           if (compactChanges.addPosts.nonEmpty) Analytics.sendEvent("graphchanges", "addPosts", "success", compactChanges.addPosts.size)
  //           if (compactChanges.addConnections.nonEmpty) Analytics.sendEvent("graphchanges", "addConnections", "success", compactChanges.addConnections.size)
  //           if (compactChanges.updatePosts.nonEmpty) Analytics.sendEvent("graphchanges", "updatePosts", "success", compactChanges.updatePosts.size)
  //           if (compactChanges.delPosts.nonEmpty) Analytics.sendEvent("graphchanges", "delPosts", "success", compactChanges.delPosts.size)
  //           if (compactChanges.delConnections.nonEmpty) Analytics.sendEvent("graphchanges", "delConnections", "success", compactChanges.delConnections.size)

  //           // flush changes that could not be sent during this transmission
  //           setTimeout(0)(flush())
  //         case _ =>
  //           Var.set(
  //             VarTuple(localChanges, newChanges ++ localChanges.now),
  //             VarTuple(changesInTransit, Nil),
  //             VarTuple(hasError, true)
  //           )

  //           println(s"failed to persist localChanges: $newChanges")
  //           Analytics.sendEvent("graphchanges", "flush", "failure", newChanges.size)
  //       }
  //     case _ => println(s"caching localChanges: $newChanges")
  //   }
  // }

  // def addChanges(
  //   addPosts:        Iterable[Post]        = Set.empty,
  //   addConnections:  Iterable[Connection]  = Set.empty,
  //   addOwnerships:   Iterable[Ownership]   = Set.empty,
  //   updatePosts:     Iterable[Post]        = Set.empty,
  //   delPosts:        Iterable[PostId]      = Set.empty,
  //   delConnections:  Iterable[Connection]  = Set.empty,
  //   delOwnerships:   Iterable[Ownership]   = Set.empty
  // )(implicit ec: ExecutionContext): Unit = {
  //   val newChanges = GraphChanges.from(addPosts, addConnections, addOwnerships, updatePosts, delPosts, delConnections, delOwnerships)

  //   addChanges(newChanges)
  // }

  // def addChangesEnriched(
  //   addPosts:        Iterable[Post]        = Set.empty,
  //   addConnections:  Iterable[Connection]  = Set.empty,
  //   addOwnerships:   Iterable[Ownership]   = Set.empty,
  //   updatePosts:     Iterable[Post]        = Set.empty,
  //   delPosts:        Iterable[PostId]      = Set.empty,
  //   delConnections:  Iterable[Connection]  = Set.empty,
  //   delOwnerships:   Iterable[Ownership]   = Set.empty
  // )(implicit ec: ExecutionContext): Unit = {
  //   val newChanges = applyEnrichmentToChanges(
  //     GraphChanges.from(addPosts, addConnections, addOwnerships, updatePosts, delPosts, delConnections, delOwnerships)
  //   )

  //   addChanges(newChanges)
  // }

  // def addChanges(newChanges: GraphChanges)(implicit ec: ExecutionContext): Unit = if (newChanges.nonEmpty) {
  //   //TODO fake info about own posts when applying
  //   // state.ownPosts ++= newChanges.addPosts.map(_.id)

  //   // we need store all deleted posts to be able to reconstruct them when
  //   // undoing post deletion (need to add them again)
  //   // deletedPostsById ++= newChanges.delPosts.map(id => state.rawGraph.now.postsById(id)).by(_.id)

  //   // redoHistory = Nil
  //   // undoHistory +:= newChanges

  //   // saveAndApplyChanges(newChanges.consistent)
  // }

  // def undoChanges()(implicit ec: ExecutionContext): Unit = if (undoHistory.nonEmpty) {
  //   val changesToRevert = undoHistory.head
  //   undoHistory = undoHistory.tail
  //   redoHistory +:= changesToRevert
  //   saveAndApplyChanges(changesToRevert.revert(deletedPostsById))
  // }

  // def redoChanges()(implicit ec: ExecutionContext): Unit = if (redoHistory.nonEmpty) {
  //   val changesToRedo = redoHistory.head
  //   redoHistory = redoHistory.tail
  //   undoHistory +:= changesToRedo
  //   saveAndApplyChanges(changesToRedo)
  // }

  //TODO how to allow setting additional changes in var.set with this one?///
  // private def saveAndApplyChanges(changes: GraphChanges)(implicit ec: ExecutionContext) {
  //   Var.set(
  //     VarTuple(canUndo, undoHistory.nonEmpty),
  //     VarTuple(canRedo, redoHistory.nonEmpty),
  //     VarTuple(localChanges, localChanges.now :+ changes),
  //     //TODO: change only the display graph in global state by adding the currentChanges to the rawgraph
  //     VarTuple(state.rawGraph, state.rawGraph.now applyChanges (currentChanges merge changes))
  //   )

  //   flush()
  // }
}

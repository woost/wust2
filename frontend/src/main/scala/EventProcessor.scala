package wust.frontend

import wust.graph._
import wust.ids._
import wust.util.collection._
import wust.api._, ApiEvent._
import boopickle.Default._
import io.circe.Decoder.state
import outwatch.Sink
import wust.frontend.views.ViewConfig

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import wust.util.Analytics

import scala.collection.mutable
import scala.scalajs.js.timers.setTimeout
import scala.concurrent.Future
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.{PublishSubject}
import monix.reactive.OverflowStrategy.Unbounded
import monix.execution.Cancelable
import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global
import outwatch.dom._
import wust.util.outwatchHelpers._

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
  case object Offline extends SyncMode

  val default = Live
  val all = Seq(Live, Offline)
}

// case class PersistencyState(undoHistory: Seq[GraphChanges], redoHistory: Seq[GraphChanges], changes: GraphChanges)

abstract class ChangeHandlers(currentUser: Observable[Option[User]]) {
  import monix.execution.Scheduler.Implicits.global
  //TODO: sinks?
  val changes = Handler.create[GraphChanges]().unsafeRunSync()

  val addPost: Sink[String] = changes.redirect { _.withLatestFrom(currentUser) { (text: String, userOpt: Option[User]) =>
    val newPost = userOpt match {
      case Some(user) => Post.newId(text, author = user.id)
      case None => Post.newId(text, author = 1) //Replaced in backend after creation of implicit user
    }
    GraphChanges(addPosts = Set(newPost))
  } }
}

object EventProcessor {

  def apply(rawEventStream: Observable[Seq[ApiEvent]], syncEnabled: Observable[Boolean], viewConfig: Observable[ViewConfig]): EventProcessor = {
    val eventStream: Observable[Seq[ApiEvent]] = {
      val partitionedEvents:Observable[(Seq[ApiEvent], Seq[ApiEvent])] = rawEventStream.map(_.partition {
        case NewGraphChanges(_) => true
        case _ => false
      })

      val graphEvents = partitionedEvents.map(_._1)
      val otherEvents = partitionedEvents.map(_._2)
      //TODO bufferunless here will crash merge for rawGraph with infinite loop.
      // somehow `x.bufferUnless(..) merge y.bufferUnless(..)` does not work.
      val bufferedGraphEvents = graphEvents //.bufferUnless(syncEnabled).map(_.flatten)

      Observable.merge(bufferedGraphEvents, otherEvents)
    }

    val currentAuth: Observable[Option[Authentication]] = eventStream.map(_.reverse.collectFirst {
      case LoggedIn(auth) => Some(auth)
      case LoggedOut => None
    }).collect { case Some(auth) => auth }.startWith(Seq(None))

    val currentUser: Observable[Option[User]] = currentAuth.map(_.map(_.user))

    new EventProcessor(eventStream, viewConfig, currentAuth, currentUser)
  }
}

class EventProcessor private(eventStream: Observable[Seq[ApiEvent]], viewConfig: Observable[ViewConfig], val currentAuth: Observable[Option[Authentication]], val currentUser: Observable[Option[User]]) extends ChangeHandlers(currentUser) {
  import monix.execution.Scheduler.Implicits.global
  // import Client.storage
  // storage.graphChanges <-- localChanges //TODO

  object enriched extends ChangeHandlers(currentUser)


  // public reader
  val (localChanges: Observable[GraphChanges], rawGraph: Observable[Graph]) = {

    // events  withLatestFrom
    // --------O----------------> localchanges
    //         ^          |
    //         |          v
    //         -----------O---->--
    //            graph

    val rawGraph = PublishSubject[Graph]()

    val enrichedChanges = enriched.changes.withLatestFrom2(rawGraph.startWith(Seq(Graph.empty)), viewConfig){ case (c,g, v) => applyEnrichmentToChanges(c,g,v) }
    val allChanges = Observable.merge(enrichedChanges, changes)
    val localChanges = allChanges.collect { case changes if changes.nonEmpty => changes.consistent }

    val localEvents = localChanges.map(c => Seq(NewGraphChanges(c)))
    val events:Observable[Seq[ApiEvent]] = Observable.merge(eventStream, localEvents)

    val graphWithChanges: Observable[Graph] = events.scan(Graph.empty) { (graph: Graph, events: Seq[ApiEvent]) => events.foldLeft(graph)(GraphUpdate.applyEvent) }

    graphWithChanges subscribe rawGraph

    (localChanges, rawGraph)
  }

  private val bufferedChanges = localChanges.map(List(_))//.bufferUnless(syncEnabled)

  private val sendingChanges = Observable.tailRecM(bufferedChanges) { changes =>
    changes.flatMap(c => Observable.fromFuture(sendChanges(c))) map {
      case true => Left(Observable.empty)
      case false => Right(Some(changes))
    }
  }
  sendingChanges.foreach(_ => ()) // trigger sendingChanges

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

  private def applyEnrichmentToChanges(changes: GraphChanges, graph: Graph, viewConfig: ViewConfig): GraphChanges = {
    import changes.consistent._

    println("DA CHANGA" + changes)

    val toDelete = delPosts.flatMap { postId =>
      Collapse.getHiddenPosts(graph removePosts viewConfig.page.parentIds, Set(postId))
    }

    val toOwn = viewConfig.groupIdOpt.toSet.flatMap { (groupId: GroupId) =>
      addPosts.map(p => Ownership(p.id, groupId))
    }

    val containedPosts = addConnections.collect { case Connection(source, Label.parent, _) => source }
    val toContain = addPosts
      .filterNot(p => containedPosts(p.id))
      .flatMap(p => Page.toParentConnections(viewConfig.page, p.id))

    val p = changes.consistent merge GraphChanges(delPosts = toDelete, addOwnerships = toOwn, addConnections = toContain)

    println("BORROWS GRAPH: " +p )
    p
  }

  private def sendChanges(changes: List[GraphChanges]): Future[Boolean] = {
    Client.api.changeGraph(changes).transform {
      case Success(success) =>
        if (success) {
          val compactChanges = changes.foldLeft(GraphChanges.empty)(_ merge _)
          if (compactChanges.addPosts.nonEmpty) Analytics.sendEvent("graphchanges", "addPosts", "success", compactChanges.addPosts.size)
          if (compactChanges.addConnections.nonEmpty) Analytics.sendEvent("graphchanges", "addConnections", "success", compactChanges.addConnections.size)
          if (compactChanges.updatePosts.nonEmpty) Analytics.sendEvent("graphchanges", "updatePosts", "success", compactChanges.updatePosts.size)
          if (compactChanges.delPosts.nonEmpty) Analytics.sendEvent("graphchanges", "delPosts", "success", compactChanges.delPosts.size)
          if (compactChanges.delConnections.nonEmpty) Analytics.sendEvent("graphchanges", "delConnections", "success", compactChanges.delConnections.size)
        } else {
          Analytics.sendEvent("graphchanges", "flush", "returned-false", changes.size)
          println(s"api request returned false: $changes")
        }

        Success(success)
      case Failure(t) =>
        println(s"api request failed '${t.getMessage}: $changes")
        Analytics.sendEvent("graphchanges", "flush", "future-failed", changes.size)

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

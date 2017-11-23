package wust.frontend

import wust.graph._
import wust.ids._
import wust.util.collection._
import wust.api.ApiEvent
import autowire._
import boopickle.Default._
import io.circe.Decoder.state
import outwatch.Sink

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import wust.util.Analytics

import scala.collection.mutable
import scala.scalajs.js.timers.setTimeout
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import outwatch.dom._
import rxscalajs.Observable
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

class GraphPersistence(syncEnabled: Observable[Boolean]) {
  import ClientCache.storage

  val enrichChanges = Handler.create[GraphChanges]().unsafeRunSync()
  val pureChanges = Handler.create[GraphChanges]().unsafeRunSync()
  val localChanges: Observable[GraphChanges] = {
    val enrichedChanges = enrichChanges.map(applyEnrichmentToChanges)
    val allChanges = enrichedChanges merge pureChanges
    allChanges.collect { case changes if changes.nonEmpty => changes.consistent }
  }

  val addPost:Sink[String] = enrichChanges.redirectMap { text =>
    val newPost = Post.newId(text)
    GraphChanges(addPosts = Set(newPost))
  }

  // storage.graphChanges <-- localChanges //TODO

  private val bufferedChanges = localChanges.map(List(_))//.bufferUnless(syncEnabled)

  private val sendingChanges = bufferedChanges.expand { (changes, number) =>
    val retryChanges = sendChanges(changes) map {
      case true => None
      case false => Some(changes)
    }

    observableFromFutureOption(retryChanges)
  }
  sendingChanges(_ => ()) // trigger sendingChanges

  def observableFromFutureOption[T](future: Future[Option[T]]): Observable[T] = {
    import scala.scalajs.js

    Observable.create[T](observer => {
      future onComplete {
        case Success(value) => value.foreach(observer.next(_)); observer.complete()
        case Failure(err) => observer.error(err.asInstanceOf[js.Any]); observer.complete()
      }
    })
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

  private def applyEnrichmentToChanges(changes: GraphChanges): GraphChanges = {
    import changes.consistent._

    changes
    //TODO
    // val toDelete = delPosts.flatMap { postId =>
    //   Collapse.getHiddenPosts(state.displayGraphWithoutParents.now.graph removePosts state.graphSelection.now.parentIds, Set(postId))
    // }

    // val toOwn = state.selectedGroupId.now.toSet.flatMap { (groupId: GroupId) =>
    //   addPosts.map(p => Ownership(p.id, groupId))
    // }

    // val containedPosts = addContainments.map(_.childId)
    // val toContain = addPosts
    //   .filterNot(p => containedPosts(p.id))
    //   .flatMap(p => GraphSelection.toContainments(state.graphSelection.now, p.id))

    // changes.consistent merge GraphChanges(delPosts = toDelete, addOwnerships = toOwn, addContainments = toContain)
  }

  private def sendChanges(changes: List[GraphChanges]): Future[Boolean] = {
    Client.api.changeGraph(changes).call().transform {
      case Success(success) =>
        if (success) {
          val compactChanges = changes.foldLeft(GraphChanges.empty)(_ merge _)
          if (compactChanges.addPosts.nonEmpty) Analytics.sendEvent("graphchanges", "addPosts", "success", compactChanges.addPosts.size)
          if (compactChanges.addConnections.nonEmpty) Analytics.sendEvent("graphchanges", "addConnections", "success", compactChanges.addConnections.size)
          if (compactChanges.addContainments.nonEmpty) Analytics.sendEvent("graphchanges", "addContainments", "success", compactChanges.addContainments.size)
          if (compactChanges.updatePosts.nonEmpty) Analytics.sendEvent("graphchanges", "updatePosts", "success", compactChanges.updatePosts.size)
          if (compactChanges.delPosts.nonEmpty) Analytics.sendEvent("graphchanges", "delPosts", "success", compactChanges.delPosts.size)
          if (compactChanges.delConnections.nonEmpty) Analytics.sendEvent("graphchanges", "delConnections", "success", compactChanges.delConnections.size)
          if (compactChanges.delContainments.nonEmpty) Analytics.sendEvent("graphchanges", "delContainments", "success", compactChanges.delContainments.size)
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
  //           if (compactChanges.addContainments.nonEmpty) Analytics.sendEvent("graphchanges", "addContainments", "success", compactChanges.addContainments.size)
  //           if (compactChanges.updatePosts.nonEmpty) Analytics.sendEvent("graphchanges", "updatePosts", "success", compactChanges.updatePosts.size)
  //           if (compactChanges.delPosts.nonEmpty) Analytics.sendEvent("graphchanges", "delPosts", "success", compactChanges.delPosts.size)
  //           if (compactChanges.delConnections.nonEmpty) Analytics.sendEvent("graphchanges", "delConnections", "success", compactChanges.delConnections.size)
  //           if (compactChanges.delContainments.nonEmpty) Analytics.sendEvent("graphchanges", "delContainments", "success", compactChanges.delContainments.size)

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
  //   addContainments: Iterable[Containment] = Set.empty,
  //   addOwnerships:   Iterable[Ownership]   = Set.empty,
  //   updatePosts:     Iterable[Post]        = Set.empty,
  //   delPosts:        Iterable[PostId]      = Set.empty,
  //   delConnections:  Iterable[Connection]  = Set.empty,
  //   delContainments: Iterable[Containment] = Set.empty,
  //   delOwnerships:   Iterable[Ownership]   = Set.empty
  // )(implicit ec: ExecutionContext): Unit = {
  //   val newChanges = GraphChanges.from(addPosts, addConnections, addContainments, addOwnerships, updatePosts, delPosts, delConnections, delContainments, delOwnerships)

  //   addChanges(newChanges)
  // }

  // def addChangesEnriched(
  //   addPosts:        Iterable[Post]        = Set.empty,
  //   addConnections:  Iterable[Connection]  = Set.empty,
  //   addContainments: Iterable[Containment] = Set.empty,
  //   addOwnerships:   Iterable[Ownership]   = Set.empty,
  //   updatePosts:     Iterable[Post]        = Set.empty,
  //   delPosts:        Iterable[PostId]      = Set.empty,
  //   delConnections:  Iterable[Connection]  = Set.empty,
  //   delContainments: Iterable[Containment] = Set.empty,
  //   delOwnerships:   Iterable[Ownership]   = Set.empty
  // )(implicit ec: ExecutionContext): Unit = {
  //   val newChanges = applyEnrichmentToChanges(
  //     GraphChanges.from(addPosts, addConnections, addContainments, addOwnerships, updatePosts, delPosts, delConnections, delContainments, delOwnerships)
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

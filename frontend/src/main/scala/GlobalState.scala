package wust.frontend

import io.circe.Decoder.state
import monocle.macros.GenLens
import vectory._
import wust.api._
import wust.frontend.views.{PageStyle, View, ViewConfig}
import wust.graph._
import wust.ids._
import org.scalajs.dom.{Event, console, window}
import org.scalajs.dom.experimental.Notification
import outwatch.dom._
import rxscalajs.subjects._
import rxscalajs.facade._
import wust.util.Analytics
import vectory._
import wust.util.outwatchHelpers._
import rxscalajs.Observable
import rx._

import scalaz.Tag

//outwatch beispiel:
// def component(handler:Handler[ViewPage]) = {
//   div(
//     span(child <-- handler.map(_.toString)),
//     button(ViewPage("hallo")) --> handler
//   )
// }

case class PostCreatorMenu(pos: Vec2) {
  var ySimPostOffset: Double = 50
}


class GlobalState(rawEventStream: Observable[Seq[ApiEvent]])(implicit ctx: Ctx.Owner) {

  import StateHelpers._
  import ClientCache.storage

  private val inner = new {
    val syncMode = Var[SyncMode](SyncMode.default) //TODO storage.syncMode
    val syncEnabled = syncMode.map(_ == SyncMode.Live)
    val viewConfig: Var[ViewConfig] = UrlRouter.variable.imap(ViewConfig.fromHash)(x => Option(ViewConfig.toHash(x)))

    val eventProcessor = EventProcessor(rawEventStream, syncEnabled.toObservable, viewConfig.toObservable)
    val rawGraph:Rx[Graph] = eventProcessor.rawGraph.toRx(seed = Graph.empty)
    val currentUser:Rx[Option[User]] = eventProcessor.currentUser.toRx(seed = None)

    val inviteToken = viewConfig.map(_.invite)

    val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view))

    val page: Var[Page] = viewConfig.zoom(GenLens[ViewConfig](_.page)).mapRead {
      rawPage =>
        rawPage() match {
          case Page.Union(ids) =>
            Page.Union(ids.filter(rawGraph().postsById.isDefinedAt))
          case s => s
        }
    }

    val pageParentPosts: Rx[Set[Post]] = Rx {
      page().parentIds.map(rawGraph().postsById)
    }

    val pageStyle = Rx {
      println(s"calculating page style: (${page()}, ${pageParentPosts()})")
      PageStyle(page(), pageParentPosts())
    }

    val selectedGroupId: Var[Option[GroupId]] = viewConfig.zoom(GenLens[ViewConfig](_.groupIdOpt)).mapRead{ groupId =>
        groupId().filter(rawGraph().groupsById.isDefinedAt)
    }


    // be aware that this is a potential memory leak.
    // it contains all ids that have ever been collapsed in this session.
    // this is a wanted feature, because manually collapsing posts is preserved with navigation
    val collapsedPostIds: Var[Set[PostId]] = Var(Set.empty)

    val currentView: Var[Perspective] = Var(Perspective()).mapRead{ perspective =>
        perspective().union(Perspective(collapsed = Selector.IdSet(collapsedPostIds())))
    }

    //TODO: when updating, both displayGraphs are recalculated
    // if possible only recalculate when needed for visualization
    val displayGraphWithoutParents: Rx[DisplayGraph] = Rx {
      val graph = groupLockFilter(viewConfig(), selectedGroupId(), rawGraph().consistent)
      page() match {
        case Page.Root =>
          currentView().applyOnGraph(graph)

        case Page.Union(parentIds) =>
          val descendants = parentIds.flatMap(graph.descendants) -- parentIds
          val selectedGraph = graph.filter(descendants)
          currentView().applyOnGraph(selectedGraph)
      }
    }


    val displayGraphWithParents: Rx[DisplayGraph] = Rx {
      val graph = groupLockFilter(viewConfig(), selectedGroupId(), rawGraph().consistent)
      page() match {
        case Page.Root =>
          currentView().applyOnGraph(graph)

        case Page.Union(parentIds) =>
          val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
          val selectedGraph = graph.filter(descendants)
          currentView().applyOnGraph(selectedGraph)
      }
    }

    val chronologicalPostsAscending = displayGraphWithoutParents.map { dg =>
      val graph = dg.graph
      graph.posts.toList.sortBy(p => Tag.unwrap(p.id))
    }

    val focusedPostId: Var[Option[PostId]] = Var(Option.empty[PostId]).mapRead{ focusedPostId =>
        focusedPostId().filter(displayGraphWithoutParents().graph.postsById.isDefinedAt)
    }

    val upButtonTargetPage:Rx[Option[Page]] = Rx {
      //TODO: handle containment cycles
      page() match {
        case Page.Root => None
        case Page.Union(parentIds) =>
          val newParentIds = parentIds.flatMap(rawGraph().parents)
          Some(if (newParentIds.nonEmpty) Page.Union(newParentIds) else Page.Root)
      }
    }
  }

  val eventProcessor = inner.eventProcessor

  val rawGraph = inner.rawGraph.toObservable
  val viewConfig = inner.viewConfig.toHandler
  val currentView = inner.currentView.toHandler
  val page = inner.page.toHandler
  val pageStyle = inner.pageStyle.toObservable
  val view = inner.view.toHandler
  val pageParentPosts = inner.pageParentPosts.toObservable
  val syncMode = inner.syncMode.toHandler
  val syncEnabled = inner.syncEnabled.toObservable
  val displayGraphWithParents = inner.displayGraphWithParents.toObservable
  val displayGraphWithoutParents = inner.displayGraphWithoutParents.toObservable
  val chronologicalPostsAscending = inner.chronologicalPostsAscending.toObservable
  val upButtonTargetPage = inner.upButtonTargetPage.toObservable


  val postCreatorMenus: Handler[List[PostCreatorMenu]] = Handler.create(List.empty[PostCreatorMenu]).unsafeRunSync()

  val jsErrors: Handler[Seq[String]] = Handler.create(Seq.empty[String]).unsafeRunSync()
  DevOnly {
    val errorMessage = Observable.create[String] { observer =>
      window.onerror = { (msg: Event, source: String, line: Int, col: Int) =>
        //TODO: send and log production js errors in backend
        observer.next(msg.toString)
      }
    }
    jsErrors <-- errorMessage.scan(Vector.empty[String])((acc, msg) => acc :+ msg)
  }

  //TODO: hack for having authorship of post. this needs to be encoded in the graph / versioning scheme
  val ownPosts = new collection.mutable.HashSet[PostId]


  //events!!
  //TODO eventProcessor?
  // rawGraph() = newGraph applyChanges eventProcessor.currentChanges
  //TODO: on user login:
  //     ClientCache.currentAuth = Option(auth)
  //     if (auth.user.isImplicit) {
  //       Analytics.sendEvent("auth", "loginimplicit", "success")
  //     }
  //     ClientCache.currentAuth = None

  // rawEventStream { events =>
  // DevOnly {
  //   views.DevView.apiEvents.updatef(events.toList ++ _)
  //   events foreach {
  //     case ReplaceGraph(newGraph) =>
  //       assert(newGraph.consistent == newGraph, s"got inconsistent graph from server:\n$newGraph\nshould be:\n${newGraph.consistent}")
  //     //TODO needed?
  //     // assert(currentUser.now.forall(user => newGraph.usersById.isDefinedAt(user.id)), s"current user is not in Graph:\n$newGraph\nuser: ${currentUser.now}")
  //     // assert(currentUser.now.forall(user => newGraph.groupsByUserId(user.id).toSet == newGraph.groups.map(_.id).toSet), s"User is not member of all groups:\ngroups: ${newGraph.groups}\nmemberships: ${newGraph.memberships}\nuser: ${currentUser.now}\nmissing memberships for groups:${currentUser.now.map(user => newGraph.groups.map(_.id).toSet -- newGraph.groupsByUserId(user.id).toSet)}")
  //     case _ =>
  //   }
  // }
  // }

  DevOnly {
    rawGraph.debug((g: Graph) => s"rawGraph: ${g.toSummaryString}")
    //      collapsedPostIds.debug("collapsedPostIds")
    currentView.debug("currentView")
    //      displayGraphWithoutParents.debug { dg => s"displayGraph: ${dg.graph.toSummaryString}" }
    //      focusedPostId.debug("focusedPostId")
    //      selectedGroupId.debug("selectedGroupId")
    // rawPage.debug("rawPage")
    page.debug("page")
    viewConfig.debug("viewConfig")
    //      currentUser.debug("\ncurrentUser")

  }

}

object StateHelpers {
  def groupLockFilter(viewConfig: ViewConfig, selectedGroupId: Option[GroupId], graph: Graph): Graph =
    if (viewConfig.lockToGroup) {
      val groupPosts = selectedGroupId.map(graph.postsByGroupId).getOrElse(Set.empty)
      graph.filter(groupPosts)
    } else graph
}

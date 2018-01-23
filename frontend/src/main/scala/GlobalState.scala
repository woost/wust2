package wust.frontend

import io.circe.Decoder.state
import monix.execution.Cancelable
import monocle.macros.GenLens
import vectory._
import wust.api._
import wust.frontend.views.{PageStyle, View, ViewConfig}
import wust.graph._
import wust.ids._
import org.scalajs.dom.{Event, console, window}
import org.scalajs.dom.experimental.Notification
import monix.reactive.OverflowStrategy.Unbounded
import outwatch.dom._
import wust.util.Analytics
import vectory._
import wust.util.outwatchHelpers._
import rx._

import scalaz.Tag

class GlobalState(implicit ctx: Ctx.Owner) {

  import StateHelpers._
  import Client.storage

  private val initialAuthentication = Client.ensureLogin()

  val inner = new {
    val syncMode = Var[SyncMode](SyncMode.default) //TODO storage.syncMode
    val syncEnabled = syncMode.map(_ == SyncMode.Live)
    val viewConfig: Var[ViewConfig] = UrlRouter.variable.imap(ViewConfig.fromHash)(x => Option(ViewConfig.toHash(x)))
    UrlRouter.variable.foreach (x => println("[S] urlrouter ABC "+ x))
    viewConfig.foreach (x => println("[S] viewConfig ABC "+ x))

    val eventProcessor = EventProcessor(Client.eventObservable, syncEnabled.toObservable, viewConfig.toObservable)
    val rawGraph:Rx[Graph] = eventProcessor.rawGraph.toRx(seed = Graph.empty)

    val currentAuth:Rx[Authentication.UserProvider] = eventProcessor.currentAuth.toRx(seed = initialAuthentication).map {
      case auth: Authentication.UserProvider => auth
      case Authentication.None => Client.ensureLogin()
    }
    //TODO: better in rx/obs operations
    currentAuth.foreach(Client.storage.auth() = _)

    rawGraph.foreach (x => println("[S] graph ABC "))

    val currentUser: Rx[User] = currentAuth.map(_.user)
    currentUser.foreach (x => println("[S] user ABC "+ x))

    val inviteToken = viewConfig.map(_.invite)

    val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view))
    view.foreach (x => println("[S] view ABC "+ x))

    val page: Var[Page] = viewConfig.zoom(GenLens[ViewConfig](_.page)).mapRead {
      rawPage =>
        rawPage() match {
          case Page.Union(ids) =>
            Page.Union(ids.filter(rawGraph().postsById.isDefinedAt))
          case s => s
        }
    }
    page.foreach (x => println("[S] page ABC "+ x))

    val pageParentPosts: Rx[Set[Post]] = Rx {
      page().parentIds.map(rawGraph().postsById)
    }
    pageParentPosts.foreach (x => println("[S] pageParentPosts ABC "+ x))

    val pageStyle = Rx {
      println(s"calculating page style: (${page()}, ${pageParentPosts()})")
      PageStyle(page(), pageParentPosts())
    }
    pageStyle.foreach (x => println("[S] pageStyle ABC "+ x))

    val selectedGroupId: Var[Option[GroupId]] = viewConfig.zoom(GenLens[ViewConfig](_.groupIdOpt)).mapRead{ groupId =>
        groupId().filter(rawGraph().groupsById.isDefinedAt)
    }

    // be aware that this is a potential memory leak.
    // it contains all ids that have ever been collapsed in this session.
    // this is a wanted feature, because manually collapsing posts is preserved with navigation
    val collapsedPostIds: Var[Set[PostId]] = Var(Set.empty)

    val currentView: Var[Perspective] = Var(Perspective()).mapRead { perspective =>
        perspective().union(Perspective(collapsed = Selector.IdSet(collapsedPostIds())))
    }
    currentView.foreach (x => println("[S] currentView ABC "+ x))

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
    displayGraphWithoutParents.foreach (x => println("[S] displayGraphWithoutParents ABC "))

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
    displayGraphWithParents.foreach (x => println("[S] displayGraphWithParents ABC "))
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

  val currentUser = inner.currentUser.toObservable
    currentUser.foreach (x => println("[O] user ABC "+ x))
  val rawGraph = inner.rawGraph.toObservable
    rawGraph.foreach (x => println("[O] graph ABC "))
  val viewConfig = inner.viewConfig.toHandler
    viewConfig.foreach (x => println("[O] viewconfig ABC "+ x))
  val currentView = inner.currentView.toHandler
   currentView.foreach (x => println("[O] currentView ABC "+ x))
  val page = inner.page.toHandler
   page.foreach (x => println("[O] page ABC "+ x))
  val pageStyle = inner.pageStyle.toObservable
   pageStyle.foreach (x => println("[O] pageStyle ABC "+ x))
  val view = inner.view.toHandler
   view.foreach (x => println("[O] view ABC "+ x))
  val pageParentPosts = inner.pageParentPosts.toObservable
   pageParentPosts.foreach (x => println("[O] pageParentPosts ABC "+ x))
  val syncMode = inner.syncMode.toHandler
  val syncEnabled = inner.syncEnabled.toObservable
  val displayGraphWithParents = inner.displayGraphWithParents.toObservable
    displayGraphWithParents.foreach (x => println("[O] displayGraphWithParents ABC "))
  val displayGraphWithoutParents = inner.displayGraphWithoutParents.toObservable
    displayGraphWithoutParents.foreach (x => println("[O] displayGraphWithoutParents ABC "))
  val upButtonTargetPage = inner.upButtonTargetPage.toObservable

  val jsErrors: Handler[Seq[String]] = Handler.create(Seq.empty[String]).unsafeRunSync()
  DevOnly {
    val errorMessage = Observable.create[String](Unbounded) { observer =>
      window.onerror = { (msg: Event, source: String, line: Int, col: Int, _: Any) =>
        //TODO: send and log production js errors in backend
        observer.onNext(msg.toString)
      }
      Cancelable()
    }
    jsErrors <-- errorMessage.scan(Vector.empty[String])((acc, msg) => acc :+ msg)
  }

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

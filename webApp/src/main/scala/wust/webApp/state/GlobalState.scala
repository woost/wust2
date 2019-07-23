package wust.webApp.state

import com.github.ghik.silencer.silent
import acyclic.file
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.experimental.permissions.PermissionState
import rx._
import wust.api._
import wust.facades.googleanalytics.Analytics
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.webApp.jsdom.Notifications
import wust.webApp.views._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ ModalConfig, Ownable }
import scala.scalajs.{ LinkingInfo, js }
import wust.webApp.jsdom.ServiceWorker
import wust.facades.googleanalytics.Analytics
import wust.facades.hotjar
import monix.eval.Task
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom.helpers.OutwatchTracing
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, UI }
import wust.api.ApiEvent.ReplaceGraph
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.StringOps
import wust.webApp.jsdom.{ Navigator, ServiceWorker }
import wust.webApp.parsers.{ UrlConfigParser, UrlConfigWriter }
import wust.webApp.{ Client, DevOnly }
import wust.facades.wdtEmojiBundle.wdtEmojiBundle
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

import scala.collection.{ breakOut, mutable }

object GlobalState {
  implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

  // register the serviceworker and get an update observable when serviceworker updates are available.
  val appUpdateIsAvailable: Observable[Unit] = if (!LinkingInfo.developmentMode) ServiceWorker.register() else Observable.empty

  val eventProcessor = EventProcessor(
    Client.observable.event,
    (changes, userId, graph) => GraphChangesAutomation.enrich(userId, graph, urlConfig, EmojiReplacer.replaceChangesToColons(changes)).consistent,
    Client.api.changeGraph,
    Client.currentAuth
  )

  val mouseClickInMainView = PublishSubject[Unit]

  def submitChanges(changes: GraphChanges) = {
    eventProcessor.changes.onNext(changes)
  }

  val leftSidebarOpen: Var[Boolean] = //TODO: replace with ADT Open/Closed
    Client.storage.sidebarOpen.imap(_ getOrElse !BrowserDetect.isMobile)(Some(_)) // expanded sidebar per default for desktop

  val urlConfig: Var[UrlConfig] = UrlRouter.variable().imap(UrlConfigParser.fromUrlRoute)(UrlConfigWriter.toUrlRoute)
  val isOnline = Observable(
    Client.observable.connected.map(_ => true),
    Client.observable.closed.map(_ => false)
  ).merge.unsafeToRx(true)

  val isLoading = Var(false)

  val hasError = Var(false)

  val fileDownloadBaseUrl = Var[Option[String]](None)

  val screenSize: Rx[ScreenSize] = outwatch.dom.dsl.events.window.onResize
    .debounce(0.2 second)
    .map(_ => ScreenSize.calculate())
    .unsafeToRx(ScreenSize.calculate())

  //TODO: replace with ADT Open/Closed
  val showTagsList = Client.storage.taglistOpen.imap(_ getOrElse false)(Some(_))

  //TODO: replace with ADT Open/Closed
  val showFilterList = Client.storage.filterlistOpen.imap(_ getOrElse false)(Some(_))

  val hoverNodeId: Var[Option[NodeId]] = Var(None)

  val askedForUnregisteredUserName: Var[Boolean] = Var(false)
  val askedForNotifications: Var[Boolean] = Var(false)

  val rightSidebarNode: Var[Option[FocusPreference]] = Var(None)

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = eventProcessor.initialAuth)
  val user: Rx[AuthUser] = auth.map(_.user)
  val userId: Rx[UserId] = user.map(_.id)

  val uploadingFiles: Var[Map[NodeId, UploadingFile]] = Var(Map.empty)

  val uiSidebarConfig: PublishSubject[Ownable[GenericSidebar.SidebarConfig]] = PublishSubject()
  val uiSidebarClose: PublishSubject[Unit] = PublishSubject()
  val uiModalConfig: PublishSubject[Ownable[ModalConfig]] = PublishSubject()
  val uiModalClose: PublishSubject[Unit] = PublishSubject()

  @silent("deprecated")
  val rawGraph: Rx[Graph] = {
    val internalGraph = eventProcessor.graph.unsafeToRx(seed = Graph.empty)

    Rx {
      val graph = internalGraph()
      val u = user()
      val newGraph =
        if (graph.contains(u.id)) graph
        else {
          graph.addNodes(
            // these nodes are obviously not in the graph for an assumed user, since the user is not persisted yet.
            // if we start with an assumed user and just create new channels we will never get a graph from the backend.
            user().toNode ::
              Nil
          )
        }

      newGraph
    }
  }

  val viewConfig: Rx[ViewConfig] = {

    var lastViewConfig: ViewConfig = ViewConfig(View.Empty, Page.empty)

    val viewAndPageAndSanitizedPage: Rx[(Option[View], Page, Page)] = Rx {
      val rawPage = urlConfig().pageChange.page
      val rawView = urlConfig().view
      (rawView, rawPage, rawPage.copy(rawPage.parentId.filter(rawGraph().contains)))
    }

    Rx {
      if (!isLoading()) {
        val tmp = viewAndPageAndSanitizedPage()
        val (rawView, rawPage, sanitizedPage) = tmp

        val visibleView: View.Visible = rawPage.parentId match {
          case None => rawView match {
            case Some(view: View.Visible) if !view.isContent => view
            case _ => View.Welcome
          }
          case Some(parentId) =>
            val bestView = ViewHeuristic(rawGraph.now, parentId, rawView, user.now.id).getOrElse(View.Empty) // use rawGraph.now to not trigger on every graph change
            scribe.debug(s"View heuristic chose new view (was $rawView): $bestView")
            bestView
        }

        lastViewConfig = ViewConfig(visibleView, sanitizedPage)
      }

      lastViewConfig
    }
  }

  val view: Rx[View.Visible] = viewConfig.map(_.view)

  val urlPage = urlConfig.map(_.pageChange.page)
  val page: Rx[Page] = viewConfig.map(_.page)
  val pageNotFound: Rx[Boolean] = Rx{ !urlConfig().pageChange.page.parentId.forall(rawGraph().contains) }

  def focus(nodeId: NodeId, needsGet: Boolean = true) = {
    urlConfig.update(_.focus(Page(nodeId), needsGet = needsGet))
  }

  val pageHasNotDeletedParents = Rx {
    page().parentId.exists(rawGraph().hasNotDeletedParents)
  }

  final case class SelectedNode(nodeId: NodeId, directParentIds: Iterable[ParentId])
  val selectedNodes: Var[Vector[SelectedNode]] = Var(Vector.empty[SelectedNode]).mapRead { selectedNodes =>
    selectedNodes().filter(data => GlobalState.graph().lookup.contains(data.nodeId))
  }

  def clearSelectedNodes(): Unit = { selectedNodes() = Vector.empty }
  def addSelectedNode(selectedNode: SelectedNode): Unit = { selectedNodes.update(_ :+ selectedNode) }
  def removeSelectedNode(nodeId: NodeId): Unit = { selectedNodes.update(_.filterNot(_.nodeId == nodeId)) }
  def toggleSelectedNode(selectedNode: SelectedNode): Unit = {
    if (selectedNodes.now.exists(_.nodeId == selectedNode.nodeId)) removeSelectedNode(selectedNode.nodeId)
    else addSelectedNode(selectedNode)
  }

  val addNodesInTransit: Rx[collection.Set[NodeId]] = {
    val changesAddNodes = eventProcessor.changesInTransit
      .map(changes => changes.flatMap(_.addNodes.map(_.id))(breakOut): mutable.HashSet[NodeId])
      .unsafeToRx(mutable.HashSet.empty)

    Rx {
      changesAddNodes() ++ uploadingFiles().keySet
    }
  }

  val isSynced: Rx[Boolean] = eventProcessor.changesInTransit.map(_.isEmpty).unsafeToRx(true)

  //TODO: wait for https://github.com/raquo/scala-dom-types/pull/36
  //  val documentIsVisible: Rx[Boolean] = {
  //    def isVisible = dom.document.visibilityState.asInstanceOf[String] == VisibilityState.visible.asInstanceOf[String]
  //
  //    events.window.eventProp[dom.Event]("visibilitychange").map(_ => isVisible).unsafeToRx(isVisible)
  //  }
  val permissionState: Rx[PermissionState] = Notifications.createPermissionStateRx()
  permissionState.triggerLater { state =>
    if (state == PermissionState.granted || state == PermissionState.denied)
      Analytics.sendEvent("notification", GlobalState.asInstanceOf[String])
  }

  val pageStyle = Rx {
    PageStyle(view(), page())
  }

  val topbarIsVisible: Rx[Boolean] = Rx{ screenSize() != ScreenSize.Small }
  @inline def smallScreen: Boolean = screenSize.now == ScreenSize.Small
  @inline def largeScreen: Boolean = screenSize.now == ScreenSize.Large

  val defaultTransformations: Seq[UserViewGraphTransformation] = Seq(GraphOperation.ExcludeDeletedChildren, GraphOperation.AutomatedHideTemplates)
  // Allow filtering / transformation of the graph on globally
  val graphTransformations: Var[Seq[UserViewGraphTransformation]] = Var(defaultTransformations)

  // transform graph with graphTransformations
  val graph: Rx[Graph] = Rx {
    val graphTrans = graphTransformations()
    val currentGraph = rawGraph()
    val currentUserId = userId()
    val currentPage = urlPage.now // use now because we do not want to trigger on page change but wait for the new raw graph, coming from each page change.

    currentPage.parentId.fold(currentGraph) { parentId =>
      if (currentGraph.contains(parentId) && graphTrans.nonEmpty) GraphOperation.filter(currentGraph, parentId, currentUserId, graphTrans)
      else currentGraph
    }
  }
  val isAnyFilterActive: Rx[Boolean] = Rx { graphTransformations().length != 2 || defaultTransformations.exists(t => !graphTransformations().contains(t)) }

  def toFocusState(viewConfig: ViewConfig): Option[FocusState] = viewConfig.page.parentId.map { parentId =>
    FocusState(viewConfig.view, parentId, parentId, isNested = false, view => urlConfig.update(_.focus(view)), nodeId => focus(nodeId))
  }
}

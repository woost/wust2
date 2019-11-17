package wust.webApp.state

// import acyclic.file
import com.github.ghik.silencer.silent
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.window
import outwatch.dom.dsl.events
import outwatch.ext.monix._
import outwatch.reactive._
import rx._
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.algorithm.dfs
import wust.webApp.jsdom.{ Notifications, ServiceWorker }
import wust.webApp.parsers.{ UrlConfigParser, UrlConfigWriter }
import wust.webApp.views._
import wust.webApp.{ Client, WoostConfig }
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, ModalConfig, Ownable }
import wust.facades.segment.Segment
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._

import scala.collection.{ breakOut, mutable }
import scala.concurrent.duration._
import scala.scalajs.{ LinkingInfo, js }
import scala.util.Try

object GlobalState {
  implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

  val automationIsDisabled = Var(false)

  val isClientOnline = SourceStream.merge(Client.observable.connected.map(_ => true), Client.observable.closed.map(_ => false)).behavior(false)
  //TODO: is browser does not trigger?!
  val isBrowserOnline = SourceStream.merge(events.window.onOffline.map(_ => false), events.window.onOnline.map(_ => true)).behavior(false)

  // register the serviceworker and get an update observable when serviceworker updates are available.
  val serviceWorkerIsActivated: SourceStream[Unit] = if (LinkingInfo.productionMode) ServiceWorker.register(WoostConfig.value.urls.serviceworker) else SourceStream.empty

  val eventProcessor = EventProcessor(
    Client.observable.event,
    (changes, userId, graph) => GraphChangesAutomation.enrich(userId, graph, urlConfig, EmojiReplacer.replaceChangesToColons(changes)).consistent,
    Client.api.changeGraph,
    Client.currentAuth,
    analyticsTrackError = Segment.trackError
  )

  eventProcessor.graph.foreach { g => scribe.debug("eventProcessor.graph: " + g) }

  val mouseClickInMainView = SinkSourceHandler.publish[Unit]

  @inline def changes = eventProcessor.changes
  def submitChanges(changes: GraphChanges) = {
    eventProcessor.changes.onNext(changes)
  }

  val leftSidebarOpen: Var[Boolean] = //TODO: replace with ADT Open/Closed
    Client.storage.sidebarOpen.imap(_ getOrElse !BrowserDetect.isMobile)(Some(_)) // expanded sidebar per default for desktop

  val urlConfig: Var[UrlConfig] = UrlRouter.variable().imap(UrlConfigParser.fromUrlRoute)(UrlConfigWriter.toUrlRoute)

  val isLoading = Var(false)

  val hasError = Var(false)

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

  val uiSidebarConfig = SinkSourceHandler.publish[Ownable[GenericSidebar.SidebarConfig]]
  val uiSidebarClose = SinkSourceHandler.publish[Unit]
  val uiModalConfig = SinkSourceHandler.publish[Ownable[ModalConfig]]
  val uiModalClose = SinkSourceHandler.publish[Unit]

  @silent("deprecated")
  val rawGraph: Rx[Graph] = {
    val internalGraph = eventProcessor.graph.unsafeToRx(seed = Graph.empty)

    Rx {
      scribe.debug("updating rawGraph rx...")
      val graph = internalGraph()
      val u = user()
      scribe.debug("  user: " + user())
      scribe.debug("  userNode: " + user().toNode)
      scribe.debug("  graph.contains(u.id): " + graph.contains(u.id))
      val newGraph =
        if (graph.contains(u.id)) graph
        else {
          scribe.debug("  adding usernode to graph")
          val g = graph.addNodes(
            // these nodes are obviously not in the graph for an assumed user, since the user is not persisted yet.
            // if we start with an assumed user and just create new channels we will never get a graph from the backend.
            user().toNode ::
              Nil
          )
          scribe.debug("  graph with added usernode: " + g)
          g
        }
      scribe.debug("  returning graph and trigger rawGraph rx...")
      newGraph
    }
  }

  val viewConfig: Rx[ViewConfig] = {

    var lastViewConfig: ViewConfig = ViewConfig(View.Empty, Page.empty, Page.empty)

    val viewAndPageAndSanitizedPageAndSubPage: Rx[(Option[View], Page, Page, Page)] = Rx {
      val rawPage = urlConfig().pageChange.page
      val rawSubPage = urlConfig().subPage
      val rawView = urlConfig().view
      (
        rawView,
        rawPage,
        rawPage.copy(rawPage.parentId.filter(rawGraph().contains)),
        rawSubPage.copy(rawSubPage.parentId.filter(rawGraph().contains))
      )
    }

    Rx {
      if (!isLoading()) {
        val tmp = viewAndPageAndSanitizedPageAndSubPage()
        val (rawView, rawPage, sanitizedPage, sanitizedSubPage) = tmp

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

        lastViewConfig = ViewConfig(visibleView, sanitizedPage, sanitizedSubPage)
      }

      lastViewConfig
    }
  }

  val view: Rx[View.Visible] = viewConfig.map(_.view)
  val viewIsContent: Rx[Boolean] = view.map(_.isContent)

  val urlPage = urlConfig.map(_.pageChange.page)
  val presentationMode: Rx[PresentationMode] = urlConfig.map(_.mode)
  val page: Rx[Page] = viewConfig.map(_.page)
  val subPage: Rx[Page] = viewConfig.map(_.subPage)
  val pageExistsInGraph: Rx[Boolean] = Rx{ page().parentId.exists(rawGraph().contains) }
  val showPageNotFound = Rx { !isLoading() && !pageExistsInGraph() && viewIsContent() }

  def focus(nodeId: NodeId, needsGet: Boolean = true) = {
    val alreadyLoaded = (
      for {
        pageId <- page.now.parentId
        pageIdx <- graph.now.idToIdx(pageId)
        nodeIdx <- graph.now.idToIdx(nodeId)
      } yield dfs.exists(_(pageIdx), dfs.withStart, graph.now.childrenIdx, isFound = { _ == nodeIdx })
    ).getOrElse(false)

    urlConfig.update(_.focus(Page(nodeId), needsGet = needsGet && !alreadyLoaded))
  }

  def focusSubPage(nodeIdOpt: Option[NodeId]) = {
    urlConfig.update(_.copy(subPage = Page(nodeIdOpt)))
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

  val isSynced: Rx[Boolean] = addNodesInTransit.map(_.isEmpty)

  //TODO: wait for https://github.com/raquo/scala-dom-types/pull/36
  //  val documentIsVisible: Rx[Boolean] = {
  //    def isVisible = dom.document.visibilityState.asInstanceOf[String] == VisibilityState.visible.asInstanceOf[String]
  //
  //    events.window.eventProp[dom.Event]("visibilitychange").map(_ => isVisible).unsafeToRx(isVisible)
  //  }
  val permissionState: Rx[PermissionState] = Notifications.createPermissionStateRx()

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

  def mainFocusState(viewConfig: ViewConfig): Option[FocusState] = viewConfig.page.parentId.map { parentId =>
    FocusState(
      view = viewConfig.view,
      contextParentId = parentId,
      focusedId = parentId,
      isNested = false,
      changeViewAction = view => urlConfig.update(_.focus(view)),
      contextParentIdAction = nodeId => focus(nodeId),
      itemIsFocused = nodeId => rightSidebarNode.map(_.exists(_.nodeId == nodeId)),
      onItemSingleClick = { focusPreference =>
        // toggle rightsidebar:
        val nextNode = if (rightSidebarNode.now.exists(_ == focusPreference)) None else Some(focusPreference)
        rightSidebarNode() = nextNode
      },
      onItemDoubleClick = { nodeId =>
        focus(nodeId)
        graph.now.nodesById(nodeId).foreach { node =>
          node.role match {
            case NodeRole.Task    => FeatureState.use(Feature.ZoomIntoTask)
            case NodeRole.Message => FeatureState.use(Feature.ZoomIntoMessage)
            case NodeRole.Note    => FeatureState.use(Feature.ZoomIntoNote)
            case _                =>
          }
        }
      },
    )
  }

  def showOnlyInFullMode(modifier: => VDomModifier, additionalModes: List[PresentationMode] = Nil)(implicit ctx: Ctx.Owner): VDomModifier = {
    GlobalState.presentationMode.map {
      case PresentationMode.Full                 => modifier
      case mode if additionalModes contains mode => modifier
      case _                                     => VDomModifier.empty
    }
  }

  private val crispIsAlreadyLoaded = Try(window.asInstanceOf[js.Dynamic].CRISP_IS_READY.asInstanceOf[Boolean]).getOrElse(false)
  val crispIsLoaded = Var(crispIsAlreadyLoaded)
  if (!crispIsAlreadyLoaded) {
    window.asInstanceOf[js.Dynamic].CRISP_READY_TRIGGER = { () =>
      crispIsLoaded() = true
    }
  }
}

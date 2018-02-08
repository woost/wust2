package wust.frontend.views.graphview

import scala.scalajs.js.JSConverters._
import d3v4._
import org.scalajs.dom
import org.scalajs.dom.html.TextArea
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{Element, console, window}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import vectory._
import wust.frontend.Color._
import wust.frontend.views.View
import wust.frontend.{DevOnly, DevPrintln, GlobalState}
import wust.graph._
import wust.util.outwatchHelpers._
import wust.util.time.time
import wust.ids._

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import wust.frontend.views.Elements._
import wust.frontend.views.Placeholders

import collection.breakOut


object SelectedPostMenu {
  def apply(postId: PostId, state: GlobalState, graphState: GraphState, transformRx: Rx[Transform])(implicit owner: Ctx.Owner) = {
    import graphState.rxPostIdToSimPost

    // without default this crashes if removed from displaygraph (eg focus / delete)
    val rxSimPost = rxPostIdToSimPost.map(_.getOrElse(postId, new SimPost(null))) //TODO: null! provide an empty default simpost
    val rxParents: Rx[Seq[Post]] = Rx {
      val graph = graphState.state.inner.displayGraphWithParents().graph
      val directParentIds = graph.parents.getOrElse(postId, Set.empty)
      directParentIds.flatMap(graph.postsById.get)(breakOut)
    }

    val transformStyle = Rx {
      val t = transformRx()
      val p = rxSimPost()

      val xOffset = -300 / 2
      val yOffset = -(p.size.y) / 2
      val x = xOffset + t.applyX(p.pos.get.x)
      val y = yOffset + t.applyY(p.pos.get.y)
      s"translate(${x}px, ${y}px)"
    }


    val parentList = rxParents.map { parents =>
      div(
        marginBottom := "5px",
        parents.map { p =>
          span(
            p.content,
            fontWeight.bold,
            backgroundColor := baseColor(p.id).toString,
            margin := "2px", padding := "1px 0px 1px 5px",
            borderRadius := "2px",
            span("×", onClick --> sideEffect {
              val addedGrandParents: Set[Connection] = {
                if (parents.size == 1)
                  state.inner.displayGraphWithParents.now.graph.parents(p.id).map(Connection(rxSimPost.now.id, Label.parent, _))
                else
                  Set.empty
              }

              state.eventProcessor.changes.unsafeOnNext(GraphChanges(
                delConnections = Set(Connection(rxSimPost.now.id, Label.parent, p.id)),
                addConnections = addedGrandParents
              ))
              ()
            }, cursor.pointer, padding := "0px 5px")
          )
        }
      )
    }


    val editMode = Handler.create[Boolean](false).unsafeRunSync()

    val updatePostHandler = Handler.create[String].unsafeRunSync()
    updatePostHandler.foreach { content =>
      val changes = GraphChanges(updatePosts = Set(rxSimPost.now.post.copy(content = content)))
      state.eventProcessor.enriched.changes.unsafeOnNext(changes)

      editMode.unsafeOnNext(false)
    }

    val insertPostHandler = Handler.create[String].unsafeRunSync()
    insertPostHandler.foreach { content =>
      val author = state.inner.currentUser.now
      val newPost = Post(PostId.fresh, content, author.id)

      val changes = GraphChanges(addPosts = Set(newPost), addConnections = Set(Connection(newPost.id, Label.parent, rxSimPost.now.id)))
      state.eventProcessor.enriched.changes.unsafeOnNext(changes)
    }

    val connectPostHandler = Handler.create[String].unsafeRunSync()
    connectPostHandler.foreach { content =>
      val author = state.inner.currentUser.now
      val newPost = Post(PostId.fresh, content, author.id)

      val changes = GraphChanges(
        addPosts = Set(newPost),
        addConnections = Set(
          Connection(rxSimPost.now.id, Label("related"), newPost.id)
        ) ++ state.inner.displayGraphWithoutParents.now.graph.parents(rxSimPost.now.id).map(parentId => Connection(newPost.id, Label.parent, parentId))
      )
      state.eventProcessor.enriched.changes.unsafeOnNext(changes)
    }

    val editableTitle = div(
      child <-- editMode.map { activated =>
        if (activated) {
          textAreaWithEnter(updatePostHandler)(rxSimPost.now.content, onInsert.map(_.asInstanceOf[TextArea]) --> sideEffect(textArea => textArea.select()))
        } else {
          div(
            child <-- rxSimPost.map(_.content).toObservable,
            textAlign := "center",
            fontSize := "150%", //simPost.fontSize,
            wordWrap := "break-word",
            display.block,
            margin := "10px",
            onClick(true) --> editMode
          )
        }
      }
    )

    //TODO: wrap in one observable
    div(
      position.absolute,
      width := "300px",
      transform <-- transformStyle.toObservable,
      div(
        child <-- rxSimPost.map(p => actionMenu(p, graphState)(zIndex := -10)).toObservable, // z-index to overlap shadow
        cls := "shadow",
        editableTitle,
        padding := "3px 5px",
        border <-- rxSimPost.map(_.border).toObservable,
        borderRadius := "5px",
        backgroundColor <-- rxSimPost.map(_.color).toObservable,
        child <-- parentList.toObservable,
        textAreaWithEnter(insertPostHandler)(Placeholders.newPost, marginTop := "20px")
      ),
      div(
        cls := "shadow",
        width := "4px",
        height := "60px",
        margin := s"0 ${(300 - 4) / 2}px",
        backgroundColor := "#8F8F8F"
      ),
      div(
        cls := "shadow",
        backgroundColor := "#F8F8F8",
        border := "2px solid #DDDDDD",
        borderRadius := "5px",
        padding := "5px",
        textAreaWithEnter(connectPostHandler)(Placeholders.newPost)
      )
    )
  }

  def actionMenu(simPost: SimPost, graphState: GraphState) = {
    div(
      cls := "shadow",
      position.absolute, top := "-55px", left := "0px",
      height := "50px", width := "300px",
      borderRadius := "5px",
      border := "2px solid #111111",
      backgroundColor := "rgba(0,0,0,0.7)", color.white,

      display.flex,
      justifyContent.spaceAround,
      alignItems.stretch,

      menuActions.filter(_.showIf(simPost, graphState)).map { action =>
        div(
          display.flex,
          flexDirection.column,
          justifyContent.center,
          flexGrow := 1,
          alignItems.center,
          span(action.name),
          onClick --> sideEffect {
            println(s"\nMenu ${action.name}: [${simPost.id}]${simPost.content}")
            graphState.selectedPostId() = None
            action.action(simPost, graphState)
            ()
          },
          //TODO: style with css a:hover
          // onmouseover := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "rgba(100,100,100,0.9)" }: js.ThisFunction),
          // onmouseout := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "transparent" }: js.ThisFunction),
          // onmousedown := { e: Event => e.preventDefault() }, // disable text selection on menu items
          cursor.pointer
        )
      }
    )
  }

  case class MenuAction(name: String, action: (SimPost, GraphState) => Unit, showIf: (SimPost, GraphState) => Boolean = (_, _) => true)

  val menuActions: List[MenuAction] = List(
    MenuAction("Focus", { (p: SimPost, gs: GraphState) => gs.state.inner.page() = Page.Union(Set(p.id)) }),
    MenuAction(
      "Collapse",
      action = (p: SimPost, gs: GraphState) => gs.rxCollapsedPostIds.update(_ + p.id),
      showIf = (p: SimPost, gs: GraphState) => !gs.rxCollapsedPostIds.now(p.id) && gs.state.inner.rawGraph.now.hasChildren(p.id)
    ),
    MenuAction(
      "Expand",
      action = (p: SimPost, gs: GraphState) => gs.rxCollapsedPostIds.update(_ - p.id),
      showIf = (p: SimPost, gs: GraphState) => gs.rxCollapsedPostIds.now(p.id) && !gs.rxDisplayGraph.now.graph.hasChildren(p.id)
    ),
    // MenuAction("Split", { (p: SimPost, s: Simulation[SimPost]) => logger.info(s"Split: ${p.id}") }),
    MenuAction("Delete", { (p: SimPost, gs: GraphState) => gs.state.eventProcessor.enriched.changes.unsafeOnNext(GraphChanges(delPosts = Set(p.id))) }),
    // MenuAction(
    //   "Autopos",
    //   { (p: SimPost) => p.fixedPos = js.undefined; d3State.simulation.alpha(0.1).restart() },
    //   showIf = (p: SimPost) => p.fixedPos.isDefined
    // ),
  )
}

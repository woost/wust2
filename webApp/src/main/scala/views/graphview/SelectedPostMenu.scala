package wust.webApp.views.graphview

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
import wust.webApp.Color._
import wust.webApp.views.View
import wust.webApp.{DevOnly, DevPrintln, GlobalState}
import wust.graph._
import wust.util.outwatchHelpers._
import wust.util.time.time
import wust.ids._

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import wust.webApp.views.Elements._
import wust.webApp.views.Placeholders

import collection.breakOut


object SelectedPostMenu {
  def apply(pos:Vec2, postId: PostId, state: GlobalState, selectedPostId:Var[Option[(Vec2, PostId)]], transformRx:Rx[d3v4.Transform])(implicit owner: Ctx.Owner) = {

    val rxPost: Rx[Post] = Rx {
      val graph = state.inner.rawGraph()
      graph.postsById.getOrElse(postId, Post.apply("", UserId(""))) //TODO: getOrElse necessary? Handle post removal.
    }

    val rxParents: Rx[Seq[Post]] = Rx {
      val graph = state.inner.displayGraphWithParents().graph
      val directParentIds = graph.parents.getOrElse(postId, Set.empty)
      directParentIds.flatMap(graph.postsById.get)(breakOut)
    }

    val transformStyle = Rx {
      val t = transformRx()
      //        val p = rxPost()
      //
      val xOffset = -300 / 2
      val yOffset = 0//-(p.size.y) / 2
      val x = xOffset + t.applyX(pos.x)
      val y = yOffset + t.applyY(pos.y)
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
                  state.inner.displayGraphWithParents.now.graph.parents(p.id).map(Connection(rxPost.now.id, Label.parent, _))
                else
                  Set.empty
              }

              state.eventProcessor.changes.onNext(GraphChanges(
                delConnections = Set(Connection(rxPost.now.id, Label.parent, p.id)),
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
    updatePostHandler.foreach { newContent =>
      val changes = GraphChanges(updatePosts = Set(rxPost.now.copy(content = newContent)))
      state.eventProcessor.enriched.changes.onNext(changes)

      editMode.unsafeOnNext(false)
    }

    val insertPostHandler = Handler.create[String].unsafeRunSync()
    insertPostHandler.foreach { content =>
      val author = state.inner.currentUser.now
      val newPost = Post(PostId.fresh, content, author.id)

      val changes = GraphChanges(addPosts = Set(newPost), addConnections = Set(Connection(newPost.id, Label.parent, rxPost.now.id)))
      state.eventProcessor.enriched.changes.onNext(changes)
    }

    val connectPostHandler = Handler.create[String].unsafeRunSync()
    connectPostHandler.foreach { content =>
      val author = state.inner.currentUser.now
      val newPost = Post(PostId.fresh, content, author.id)

      val changes = GraphChanges(
        addPosts = Set(newPost),
        addConnections = Set(
          Connection(rxPost.now.id, Label("related"), newPost.id)
        ) ++ state.inner.displayGraphWithoutParents.now.graph.parents(rxPost.now.id).map(parentId => Connection(newPost.id, Label.parent, parentId))
      )
      state.eventProcessor.enriched.changes.onNext(changes)
    }

    val editableTitle = div(
      child <-- editMode.map { activated =>
        if (activated) {
          textAreaWithEnter(updatePostHandler)(rxPost.now.content, onInsert.map(_.asInstanceOf[TextArea]) --> sideEffect(textArea => textArea.select()))
        } else {
          div(
            child <-- rxPost.map(_.content).toObservable,
            textAlign := "center",
            fontSize := "150%", //post.fontSize,
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
      onClick --> sideEffect(_.stopPropagation()), // prevent click from bubbling to background, TODO: same for dragging
      width := "300px",
      transform <-- transformStyle.toObservable,
      div(
        child <-- rxPost.map(p => actionMenu(p, state, selectedPostId)(zIndex := -10)).toObservable, // z-index to overlap shadow
        cls := "shadow",
        editableTitle,
        padding := "3px 5px",
//        border <-- rxPost.map(_.border).toObservable, //TODO: pass in staticdata
        //        backgroundColor <-- rxPost.map(_.color).toObservable,
        backgroundColor := "#EEE",
        borderRadius := "5px",
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

  def actionMenu(post: Post, graphState: GlobalState, selectedPostId:Var[Option[(Vec2,PostId)]]) = {
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

      menuActions.filter(_.showIf(post, graphState)).map { action =>
        div(
          display.flex,
          flexDirection.column,
          justifyContent.center,
          flexGrow := 1,
          alignItems.center,
          span(action.name),
          onClick --> sideEffect { event =>
            event.stopPropagation()

            println(s"\nMenu ${action.name}: [${post.id}]${post.content}")
            selectedPostId() = None
            action.action(post, graphState)
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

  case class MenuAction(name: String, action: (Post, GlobalState) => Unit, showIf: (Post, GlobalState) => Boolean = (_, _) => true)

  val menuActions: List[MenuAction] = List(
    MenuAction("Focus", { (p: Post, state: GlobalState) => state.inner.page() = Page.Union(Set(p.id)) }),
//    MenuAction(
//      "Collapse",
//      action = (p: Post, gs: GraphState) => gs.rxCollapsedPostIds.update(_ + p.id),
//      showIf = (p: Post, gs: GraphState) => !gs.rxCollapsedPostIds.now(p.id) && gs.state.inner.rawGraph.now.hasChildren(p.id)
//    ),
//    MenuAction(
//      "Expand",
//      action = (p: Post, gs: GraphState) => gs.rxCollapsedPostIds.update(_ - p.id),
//      showIf = (p: Post, gs: GraphState) => gs.rxCollapsedPostIds.now(p.id) && !gs.rxDisplayGraph.now.graph.hasChildren(p.id)
//    ),
    // MenuAction("Split", { (p: Post, s: Simulation[Post]) => logger.info(s"Split: ${p.id}") }),
    MenuAction("Delete", { (p: Post, state: GlobalState) => state.eventProcessor.enriched.changes.onNext(GraphChanges(delPosts = Set(p.id))) }),
    // MenuAction(
    //   "Autopos",
    //   { (p: Post) => p.fixedPos = js.undefined; d3State.simulation.alpha(0.1).restart() },
    //   showIf = (p: Post) => p.fixedPos.isDefined
    // ),
  )
}

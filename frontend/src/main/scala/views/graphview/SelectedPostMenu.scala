package wust.frontend.views.graphview

import monix.execution.Scheduler.Implicits.global
import scala.scalajs.js.JSConverters._
import org.scalajs.d3v4._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{Element, window, console}
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
  def apply(postId: PostId, state: GlobalState, graphState:GraphState, transformRx: Rx[Transform])(implicit owner: Ctx.Owner) = {
    import graphState.rxPostIdToSimPost
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

    //TODO: wrap in one observable
    div(
      position.absolute,
      width := "300px",
      transform <-- transformStyle.toObservable,
      div(
        child <-- rxSimPost.map(p => actionMenu(p, graphState)(zIndex := -10)).toObservable, // z-index to overlap shadow
        cls := "shadow",
        child <-- rxSimPost.map(_.content).toObservable, //        editableTitle,
        padding := "3px 5px",
        border <-- rxSimPost.map(_.border).toObservable,
        borderRadius := "5px",
        backgroundColor <-- rxSimPost.map(_.color).toObservable,
//        parentList,
//        div(insertForm, marginTop := "20px")
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
//        connectForm
      )
    )
  }

  def actionMenu(simPost:SimPost, graphState:GraphState) = {
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

       menuActions.filter(_.showIf(simPost, graphState)).map{ action =>
         div(
           display.flex,
           flexDirection.column,
           justifyContent.center,
           flexGrow := 1,
           alignItems.center,
           span(action.name),
//           onclick := { () =>
//             println(s"\nMenu ${action.name}: [${simPost.id}]${simPost.title}")
//             rxFocusedSimPost() = None
//             action.action(simPost)
//           },
           // onmouseover := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "rgba(100,100,100,0.9)" }: js.ThisFunction),
           // onmouseout := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "transparent" }: js.ThisFunction),
           // onmousedown := { e: Event => e.preventDefault() }, // disable text selection on menu items
           cursor.pointer
         )
       }
     )
  }

  case class MenuAction(name: String, action: (SimPost,GraphState) => Unit, showIf: (SimPost,GraphState) => Boolean = (_,_) => true)
  val menuActions:List[MenuAction] = List(
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
    MenuAction("Delete", { (p: SimPost, gs:GraphState) => gs.state.eventProcessor.enriched.changes.unsafeOnNext(GraphChanges(delPosts = Set(p.id))) }),
    // MenuAction(
    //   "Autopos",
    //   { (p: SimPost) => p.fixedPos = js.undefined; d3State.simulation.alpha(0.1).restart() },
    //   showIf = (p: SimPost) => p.fixedPos.isDefined
    // ),
  )
}

package wust.frontend.views.graphview

import autowire._
import boopickle.Default._
import org.scalajs.d3v4._
import rx._
import rxext._
import wust.frontend._
import wust.util.collection._
import wust.ids._
import wust.graph._
import wust.frontend.views.{Elements}
import org.scalajs.dom.raw.{HTMLElement}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.math._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import wust.graph.GraphSelection
import wust.util.EventTracker.sendEvent
import collection.breakOut
import wust.frontend.Color._

class PostMenuSelection(graphState: GraphState, d3State: D3State)(implicit ctx: Ctx.Owner) extends DataSelection[SimPost] {
  import graphState.state.persistence

  val menuActions = (
    MenuAction("Focus", { (p: SimPost) => graphState.state.graphSelection() = GraphSelection.Union(Set(p.id)) }) ::
    MenuAction(
      "Collapse",
      action = (p: SimPost) => graphState.rxCollapsedPostIds.updatef(_ + p.id),
      showIf = (p: SimPost) => !graphState.rxCollapsedPostIds.now(p.id) && graphState.state.rawGraph.now.hasChildren(p.id)
    ) ::
      MenuAction(
        "Expand",
        action = (p: SimPost) => graphState.rxCollapsedPostIds.updatef(_ - p.id),
        showIf = (p: SimPost) => graphState.rxCollapsedPostIds.now(p.id) && !graphState.rxDisplayGraph.now.graph.hasChildren(p.id)
      ) ::
        // MenuAction("Split", { (p: SimPost, s: Simulation[SimPost]) => logger.info(s"Split: ${p.id}") }) ::
        MenuAction("Delete", { (p: SimPost) => persistence.addChangesEnriched(delPosts = Set(p.id)); sendEvent("post", "delete", "api") }) ::
        // MenuAction(
        //   "Autopos",
        //   { (p: SimPost) => p.fixedPos = js.undefined; d3State.simulation.alpha(0.1).restart() },
        //   showIf = (p: SimPost) => p.fixedPos.isDefined
        // ) ::
        Nil
  )

  override val tag = "div"
  override def enter(menu: Enter[SimPost]) {
    menu.append { (simPost: SimPost) =>
      import graphState.rxFocusedSimPost
      import graphState.rxPostIdToSimPost
      import scalatags.JsDom.all._
      import org.scalajs.dom.{Event}
      import org.scalajs.dom.raw.{HTMLTextAreaElement}
      import scalatags.rx.all._
      import graphState.state.persistence
      import Elements.{inlineTextarea, textareaWithEnter}

      //TODO: cannot nest more divs here. Maybe because of d3 nested selections?
      def div = span(display.block) // this is a workaround to avoid using divs

      // without default this crashes if removed from displaygraph (eg focus / delete)
      val rxSimPost = rxPostIdToSimPost.map(_.getOrElse(simPost.id, new SimPost(Post("", ""))))
      val rxParents: Rx[Seq[Post]] = graphState.state.rawGraph.map{ graph =>
        graph.parents.getOrElse(simPost.id, Set.empty).flatMap(graph.postsById.get)(breakOut)
      }

      def submitInsert(field: HTMLTextAreaElement) = {
        val newPost = Post.newId(field.value)
        persistence.addChangesEnriched(addPosts = Set(newPost), addContainments = Set(Containment(simPost.id, newPost.id)))
        field.value = ""
        false
      }
      val insertField: HTMLTextAreaElement = textareaWithEnter(submitInsert)(placeholder := "Insert new post", width := "100%").render
      val insertForm = form(
        insertField,
        // input(tpe := "submit", "insert"),
        onsubmit := { (e: Event) =>
          submitInsert(insertField)
          e.preventDefault()
        }
      ).render

      def submitConnect(field: HTMLTextAreaElement) = {
        val newPost = Post.newId(field.value)
        persistence.addChangesEnriched(
          addPosts = Set(newPost),
          addConnections = Set(Connection(simPost.id, newPost.id)),
          addContainments = graphState.state.displayGraph.now.graph.parents(simPost.id).map(parentId => Containment(parentId, newPost.id))
        )
        field.value = ""
        false
      }
      val connectField: HTMLTextAreaElement = textareaWithEnter(submitConnect)(placeholder := "Create new connected post", width := "100%").render
      val connectForm = form(
        connectField,
        // input(tpe := "submit", "insert"),
        onsubmit := { (e: Event) =>
          submitConnect(connectField)
          e.preventDefault()
        }
      ).render

      val editMode = Var(false)
      def submitEdit(field: HTMLTextAreaElement) = {
        persistence.addChanges(updatePosts = Set(simPost.post.copy(title = field.value)))
        editMode() = false
      }
      val editField = inlineTextarea(
        submitEdit,
        cancel = () => editMode() = false
      )(width := "100%").render
      val editForm = form(
        editField,
        onsubmit := { (e: Event) =>
          submitEdit(editField)
          e.preventDefault()
        }
      ).render

      val editableTitle = editMode.map{ activated =>
        if (activated) {
          editField.value = rxSimPost.now.title // .now, because we don't want an update to destroy our current edit
          editForm
        } else div(
          rxSimPost.map(_.title), //TODO: edit symbol
          textAlign.center,
          fontSize := "150%", //simPost.fontSize,
          wordWrap := "break-word",
          display.block,
          margin := "10px",
          onclick := { () => editMode() = true }
        ).render
      }

      val parentList = rxParents.map(parents => div(marginBottom := "5px", parents.map{ p =>
        span(
          p.title,
          fontWeight.bold,
          backgroundColor := baseColor(p.id).toString,
          margin := "2px", padding := "1px 0px 1px 5px",
          borderRadius := "2px",
          span("×", onclick := { () => persistence.addChanges(delContainments = Set(Containment(p.id, simPost.id))) }, cursor.pointer, padding := "0px 5px")
        )
      }).render)

      //TODO: hide postMenu with ESC key

      val actionMenu = div(
        cls := "shadow",
        position.absolute, top := "-55px", left := "0px",
        height := "50px", width := "300px",
        borderRadius := "5px",
        border := "2px solid #111111",
        backgroundColor := "rgba(0,0,0,0.7)", color.white,

        display.flex,
        justifyContent.spaceAround,
        alignItems.stretch,

        menuActions.filter(_.showIf(simPost)).map{ action =>
          div(
            display.flex,
            flexDirection.column,
            justifyContent.center,
            flexGrow := "1",
            alignItems.center,
            span(action.name),
            onclick := { () =>
              println(s"\nMenu ${action.name}: [${simPost.id}]${simPost.title}")
              //TODO: change two reactive variables at once?
              rxFocusedSimPost() = None
              action.action(simPost)
            },
            onmouseover := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "rgba(100,100,100,0.9)" }: js.ThisFunction),
            onmouseout := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "transparent" }: js.ThisFunction),
            onmousedown := { e: Event => e.preventDefault() }, // disable text selection on menu items
            cursor.pointer
          )
        }
      )

      val menu = scalatags.JsDom.all.div(
        position.absolute,
        width := "300px", //rxSimPost.map(simPost => s"${simPost.size.x max 300}px"),
        div(
          actionMenu(zIndex := "-10"), // z-index to overlap shadow
          cls := "shadow",
          editableTitle,
          padding := "3px 5px",
          border := rxSimPost.map(_.border),
          borderRadius := "5px",
          backgroundColor := rxSimPost.map(_.color),
          parentList,
          div(insertForm, marginTop := "20px")
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
          connectForm
        )
      ).render

      d3.select(menu).node()
    }

  }

  override def draw(menu: Selection[SimPost]) {
    menu.style("transform", { (p: SimPost) =>
      val xOffset = -300 / 2 //-(p.size.x max 300) / 2
      val yOffset = -(p.size.y) / 2
      val x = xOffset + d3State.transform.applyX(p.x)
      val y = yOffset + d3State.transform.applyY(p.y)
      s"translate(${x}px, ${y}px)"
    })
  }
}

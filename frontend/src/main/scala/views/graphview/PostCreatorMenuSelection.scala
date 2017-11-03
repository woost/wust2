package wust.frontend.views.graphview

import org.scalajs.d3v4._
import rx._
import wust.frontend._
import wust.frontend.views.Elements
import wust.graph._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.math._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import wust.graph.GraphSelection
import collection.breakOut
import wust.frontend.Color._
import scala.scalajs.js.timers.setTimeout

class CreatePostMenuSelection(graphState: GraphState, d3State: D3State)(implicit ctx: Ctx.Owner) extends DataSelection[PostCreatorMenu] {
  import graphState.state.persistence

  override val tag = "div"
  override def enter(menu: Enter[PostCreatorMenu]) {
    menu.append { (postCreatorMenu: PostCreatorMenu) =>
      import Elements.textareaWithEnter
      import graphState.rxPostIdToSimPost
      import org.scalajs.dom.Event
      import org.scalajs.dom.raw.HTMLTextAreaElement

      import scalatags.JsDom.all._

      //TODO: cannot nest more divs here. Maybe because of d3 nested selections?
      def div = span(display.block) // this is a workaround to avoid using divs

      def submitInsert(field: HTMLTextAreaElement) = {
        val newPost = Post.newId(field.value)
        persistence.addChangesEnriched(addPosts = Set(newPost))
        val simPostOpt = rxPostIdToSimPost.now.get(newPost.id)
        simPostOpt.foreach { simPost =>
          simPost.fx = postCreatorMenu.pos.x
          simPost.fy = postCreatorMenu.pos.y + postCreatorMenu.ySimPostOffset / d3State.transform.k + simPost.size.height / 2
          postCreatorMenu.ySimPostOffset += (simPost.size.height + 10) * d3State.transform.k
        }
        field.value = ""
        false
      }
      val insertField: HTMLTextAreaElement = textareaWithEnter(submitInsert)(placeholder := "Create new post. Press Enter to submit.", width := "100%").render
      val insertForm = form(
        insertField,
        // input(tpe := "submit", "insert"),
        onsubmit := { (e: Event) =>
          submitInsert(insertField)
          e.preventDefault()
        }
      ).render

      //TODO: hide postMenu with ESC key
      //TODO: checkboxes for parents
      //TODO: select for group
      //TODO: close button

      val menu = scalatags.JsDom.all.div(
        position.absolute,
        width := "300px",
        div(
          insertForm,
          cls := "shadow",
          padding := "3px 5px",
          border := "2px solid #DDDDDD",
          borderRadius := "5px",
          backgroundColor := "#F8F8F8"
        )
      ).render

      setTimeout(100) { // TODO: this is a workaround
        insertField.focus()
      }

      d3.select(menu).node()
    }

  }

  override def draw(menu: Selection[PostCreatorMenu]) {
    menu.style("transform", { (p: PostCreatorMenu) =>
      val xOffset = -300 / 2
      val yOffset = -30
      val x = xOffset + d3State.transform.applyX(p.pos.x)
      val y = yOffset + d3State.transform.applyY(p.pos.y)
      s"translate(${x}px, ${y}px)"
    })
  }
}

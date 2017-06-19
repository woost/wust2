package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag
import scala.math.Ordering

import org.scalajs.dom.{window, document, console}
import org.scalajs.dom.raw.{Text, Element, HTMLElement}
import org.scalajs.dom.raw.{HTMLTextAreaElement}
import org.scalajs.dom.{Event}
import scalatags.JsDom.all._
import scala.scalajs.js
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{DragEvent, KeyboardEvent}
import collection.breakOut
import wust.frontend.Color._
import org.scalajs.d3v4._
import Elements.{inlineTextarea, textareaWithEnter}

object BoardView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state.persistence

    val focusedParentIds = state.graphSelection.map(_.parentIds)

    val headLineText = Rx {
      val parents = focusedParentIds().map(state.rawGraph().postsById)
      val parentTitles = parents.map(_.title).mkString(", ")
      parentTitles

    }

    val bgColor = Rx {
      val mixedDirectParentColors = mixColors(focusedParentIds().map(baseColor))
      mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString
    }

    val columnsRx: Rx[Seq[(Post, Seq[Post])]] = Rx {
      val graph = state.displayGraphWithoutParents().graph
      val columns = graph.postIds.filter(!graph.hasParents(_)).map(graph.postsById(_))(breakOut)
      columns.map{ column =>
        val items = graph.transitiveChildren(column.id).map(graph.postsById(_))(breakOut)
        (column, items)
      }
    }

    div(
      height := "100%",
      backgroundColor := bgColor,
      h1(headLineText, marginLeft := "10px"),
      padding := "10px",

      columnsRx.map{ columns =>
        div(
          display.flex,
          columns.map{
            case (column, items) =>
              val columnColor = mixColors(List(baseColor(column.id), d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString
              div(
                flexGrow := "1",
                border := "1px solid #989898",
                borderRadius := "3px",
                margin := "10px",
                padding := "3px",
                backgroundColor := columnColor,
                h2(
                  column.title,
                  textAlign := "center",
                ),
                items.map{ item =>
                  div(
                    item.title,
                    backgroundColor := "#FFF",
                    border := "1px solid #BBB",
                    borderRadius := "3px",
                    padding := "15px 10px",
                    margin := "5px",
                    draggable := "true",
                    cursor.move,
                    ondragstart := { (e:DragEvent) => 
                      //TODO: encoding both ids as a string feels very wrong. Could we at least use an js.array or json?
                      e.dataTransfer.setData("text/plain", s"${Tag.unwrap(column.id)} ${Tag.unwrap(item.id)}")
                      
                    }
                  )
                },
                ondragover := {(e:DragEvent) =>
                  e.preventDefault();
                  e.dataTransfer.dropEffect = "move"
                },

                ondrop := {(e:DragEvent) => 
                  val data = e.dataTransfer.getData("text").split(" ")
                  println(data)
                  val sourceColumnId = PostId(data(0))
                  val itemId = PostId(data(1))
                  if(sourceColumnId != column.id) {
                    println(sourceColumnId + " -> " + itemId + " -> " + column.id)
                    persistence.addChanges(
                      delContainments = Set(Containment(sourceColumnId, itemId)),
                      addContainments = Set(Containment(column.id, itemId)),
                      )
                  }
                },
                {
                  def submitInsert(field: HTMLTextAreaElement) = {
                    val newPost = Post.newId(field.value)
                    persistence.addChangesEnriched(addPosts = Set(newPost), addContainments = Set(Containment(column.id, newPost.id)))
                    field.value = ""
                    false
                  }
                  val insertField: HTMLTextAreaElement = textareaWithEnter(submitInsert)(placeholder := "Insert new post", width := "100%").render
                  val insertForm = form(
                    insertField,
                    onsubmit := { (e: Event) =>
                      submitInsert(insertField)
                      e.preventDefault()
                    }
                    ).render
                  div(
                    margin := "5px",
                    marginTop := "15px",
                    insertForm
                  )      
                }
              ).render
          },
        ).render
      },

                {
                  def submitInsert(field: HTMLTextAreaElement) = {
                    val newPost = Post.newId(field.value)
                    persistence.addChangesEnriched(addPosts = Set(newPost))
                    field.value = ""
                    false
                  }
                  val insertField: HTMLTextAreaElement = textareaWithEnter(submitInsert)(placeholder := "Add new column", width := "100%").render
                  val insertForm = form(
                    insertField,
                    onsubmit := { (e: Event) =>
                      submitInsert(insertField)
                      e.preventDefault()
                    }
                    ).render
                  div(
                    margin := "10px",
                    marginTop := "15px",
                    insertForm
                  )      
                }
    )
  }
}

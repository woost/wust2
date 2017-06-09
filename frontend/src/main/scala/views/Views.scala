package wust.frontend.views

import autowire._
import boopickle.Default._
import wust.frontend.{GlobalState, Client}
import wust.frontend.Color._
import wust.graph._
import wust.ids._
import wust.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scalatags.JsDom.all._

object Views {
  //TODO: this is only used in GraphView. Therefore move to PostSelection.scala
  def post(post: Post) = div(
    post.title,
    maxWidth := "10em",
    wordWrap := "break-word",
    padding := "3px 5px",
    border := "1px solid #444",
    borderRadius := "3px"
  )
}

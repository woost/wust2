package wust.webApp.views

import wust.webApp.Avatar
import outwatch.dom.VNode
import rx.Ctx
import wust.webApp.GlobalState
import outwatch.dom.dsl._
import outwatch.dom._
import outwatch._
import Math._
import collection.mutable

import colorado.HCL

object AvatarView extends View {
  override val key = "avatar"
  override val displayName = "Avatar"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val n = 100
    val size = 40
    div(
      width := "100%",
      overflow.auto,
      div(
        padding := "10px",
        display := "grid",
        style("grid-gap") := "10px",
        style("grid-template-columns") := s"repeat(auto-fill, minmax(${size}px, 1fr))",
        List.tabulate(n)(i => Avatar(i, 5, false)(width := s"${size}px", height := s"${size}px")  ),
        List.tabulate(n)(i => Avatar(i, 7, true)(width := s"${size}px", height := s"${size}px")  ),
        //{
        //  //~290us
        //  val sw = new wust.util.time.StopWatch()
        //  sw.benchmark(10000)(avatarGrid2(scala.util.Random.nextInt(), size, 9, true))
        //  sw.readMicros.toInt //TODO: Outwatch: accept Long
        //}
      )
    )
  }
}

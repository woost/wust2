package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx.Ctx
import wust.webApp.state.GlobalState

object AvatarView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val n = 100
    val size = 40
    div(
      width := "100%",
      overflow.auto,
      div(
        padding := "10px",
        // display := "grid",
        // style("grid-gap") := "10px",
        // style("grid-template-columns") := s"repeat(auto-fill, minmax(${size}px, 1fr))",
        (5 to 5).map { res =>
          div(
            width := s"${n * (size + 10)}px",
            marginBottom := "10px",
            List.tabulate(n)(
              i =>
                Avatar.verticalMirror(i, res)(
                  width := s"${size}px",
                  height := s"${size}px",
                  display.inlineBlock,
                  marginRight := "10px"
                )
            )
          ),
        },
        (8 to 10).map { res =>
          div(
            width := s"${n * (size + 10)}px",
            marginBottom := "10px",
            List.tabulate(n)(
              i =>
                Avatar.twoMirror(i, res)(
                  width := s"${size}px",
                  height := s"${size}px",
                  display.inlineBlock,
                  marginRight := "10px"
                )
            )
          ),
        },
        // List.tabulate(n)(i => Avatar(i, 10, false)(width := s"${size}px", height := s"${size}px")  ),
        // div(),
        // List.tabulate(n)(i => Avatar(i, 11, false)(width := s"${size}px", height := s"${size}px")  ),
        // div(),
        // List.tabulate(n)(i => Avatar(i, 12, false)(width := s"${size}px", height := s"${size}px")  ),
        // div(),
        // List.tabulate(n)(i => Avatar(i, 13, true)(width := s"${size}px", height := s"${size}px")  ),
        // // div(),
        // List.tabulate(n)(i => Avatar(i, 25, true)(width := s"${size}px", height := s"${size}px")  ),
        //{
        //  //~290us
        //  val sw = new wust.util.time.StopWatch()
        //  sw.benchmark(100000)(Avatar.twoMirror(scala.util.Random.nextInt(), 10))
        //  sw.readMicros.toInt //TODO: Outwatch: accept Long
        //}
      )
    )
  }
}

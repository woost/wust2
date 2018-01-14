package wust.frontend.views

import monix.execution.Scheduler.Implicits.global
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend.GlobalState

sealed trait RestructuringTask {
  val title: String
  val description: String
  def component(state: GlobalState): VNode

  def render(state: GlobalState) = {
    div( //modal outer container
      div( //modal inner container
        div( //header
          padding := "2px 16px",
          backgroundColor := "green",
          color := "black",
        ),
        div( //content
          div(
            title,
            span(
              "×",
              onClick(false) --> RestructuringTaskGenerator.taskDisplay,
              cursor.pointer,
              float.right,
              fontSize := "28px",
              fontWeight.bold,
            ),
            width := "100%",
          ),
          p(description),
          div(
            "TASK HERE",
            // children <-- component(state) // This differs for every task
            component(state),
          ),
          padding := "2px 16px",
        ),
        div(//footer
          padding := "2px 16px",
          backgroundColor := "green",
          color := "black",
        ),
        width := "90%",
        position.fixed,
        left := "0",
        right := "0",
        bottom := "0",
        margin := "0 auto",
        border := "1px solid #888",
        boxShadow := "0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19)",
        backgroundColor := "gray",
      ),
      display.block,
      position.fixed,
      zIndex := 100,
      left := "0",
      bottom := "0",
      width := "100%",
      overflow.auto,
      backgroundColor := "rgb(0,0,0)",
      backgroundColor := "rgba(0,0,0,0.4)",
    )
  }

}

// Multiple Post RestructuringTask
case object ConnectPosts extends RestructuringTask
{
  val title = "Connect Posts"
  val description = "Do these posts belong together?"
  def component(state: GlobalState): VNode = {
    div()
  }
}
case object MergePosts extends RestructuringTask
{
  val title = "Merge Posts"
  val description = "Should these post be merged together?"
  def component(state: GlobalState): VNode = {
    div()
  }
}

// Single Post RestructuringTask
case object SplitPost extends RestructuringTask
{
  val title = "Split Post"
  val description = "Should this post be split into 2 separate posts?"
  def component(state: GlobalState): VNode = {
    div()
  }
}

case object AddTagToPost extends RestructuringTask
{
  val title = "Add tag to post"
  val description = "Is there a tag describing this post?"
  def component(state: GlobalState): VNode = {
    div()
  }
}

case object RestructuringTaskGenerator {
  val allTasks: List[RestructuringTask] = List(ConnectPosts , MergePosts , SplitPost , AddTagToPost)

  def apply(globalState: GlobalState) = {
     val show = taskDisplay.map(d => {
      println(s"display task! ${d.toString}")
      if(d == true) {
        RestructuringTaskChooser.heuristic(allTasks).render(globalState)
      } else {
        renderButton
      }
    })

    div(
      child <-- show,
    )
  }

  val taskDisplay = Handler.create[Boolean](false).unsafeRunSync()

  def renderButton = div(
    span("Tasks"),
    fontWeight.bold,
    fontSize := "20px",
    marginBottom := "10px",
    button("Task me!", width := "100%", onClick(true) --> taskDisplay),
  )
}

case object RestructuringTaskChooser {
  def random(tasks: List[RestructuringTask]): RestructuringTask = {
    tasks(scala.util.Random.nextInt(tasks.size))
  }

  def heuristic: List[RestructuringTask] => RestructuringTask = random
}

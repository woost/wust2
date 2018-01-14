package wust.frontend.views

import monix.execution.Scheduler.Implicits.global
import org.scalajs.dom.raw.MouseEvent
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend.{Client, GlobalState}
import wust.graph.{GraphChanges, Post}

case object Style {
  def post(post: Post) = div(
    display.block,
    width := "100%",
    padding := "5px 10px",
    margin := "5px 0px",
    p(
      post.content,
      color.black,
      maxWidth := "60%",
      backgroundColor := "#eee",
      padding := "5px 10px",
      borderRadius := "7px",
      border := "1px solid gray",
      margin := "5px 0px",
      cursor.pointer // TODO: What about cursor when selecting text?
    ),
  )
}

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
    div( )
  }
}
case object MergePosts extends RestructuringTask
{
  val title = "Merge Posts"
  val description = "Does these posts state the same and are redundant? If yes, they will be merged."
  def component(state: GlobalState): VNode = {
    div()
  }
}
case object UnifyPosts extends RestructuringTask
{
  val title = "Unify Posts"
  val description = "Does these posts state the same and are redundant? If yes, they will be unified."
  def component(state: GlobalState): VNode = {
    div()
  }
}

// Single Post RestructuringTask
case object DeletePost extends RestructuringTask
{
  val title = "Delete Post"
  val description = "Is this posts irrelevant for this discussion? (e.g. Hello post)"

  def deletePost(post: Post) = {
    println(s"Deleting post: ${post.id}")
    val changes = List(GraphChanges(delPosts = Set(post.id)))

    Client.api.changeGraph(changes).foreach(res => println("Api call succeeded: " + res.toString))

  }
  def keepPost(post: Post) = {
    println(s"Keeping post: ${post.id}")
  }

  def component(state: GlobalState): VNode = {
    val answerYes = Handler.create[MouseEvent]
    val answerNo = Handler.create[MouseEvent]

    val currentPosts = state.inner.displayGraphWithoutParents.now.graph.posts.toSet
    val delPost = choosePosts(currentPosts, TaskHeuristic.random)

    div(
      Style.post(delPost),
      div(
        button("Yes", onClick(answerYes) --> sideEffect(deletePost(delPost)), onClick(false) --> RestructuringTaskGenerator.taskDisplay),
        button("No", onClick(answerNo) --> sideEffect(keepPost(delPost)), onClick(false) --> RestructuringTaskGenerator.taskDisplay),
        width := "100%",
      )
    )
  }

  def choosePosts(posts: Set[Post], deletePostHeuristic: (Set[Post], Int) => Set[Post]) = {
    val p = deletePostHeuristic(posts, 1)
    p.head
  }
}

case object SplitPost extends RestructuringTask
{
  val title = "Split Post"
  val description = "Does this Post contain multiple statements? Please split the post."
  def component(state: GlobalState): VNode = {
    div()
  }
}

case object AddTagToPost extends RestructuringTask
{
  val title = "Add tag to post"
  val description = "How would you describe this post? Please add a tag."
  def component(state: GlobalState): VNode = {
    div()
  }
}

case object AddTagToConnection extends RestructuringTask
{
  val title = "Add tag to connection"
  val description = "How would you describe the relation between these posts? Please add a tag to the relation."
  def component(state: GlobalState): VNode = {
    div()
  }
}

case object RestructuringTaskGenerator {
  val allTasks: List[RestructuringTask] = List(ConnectPosts , MergePosts, UnifyPosts, DeletePost, SplitPost , AddTagToPost, AddTagToConnection)

  def apply(globalState: GlobalState) = {
     val show = taskDisplay.map(d => {
      println(s"display task! ${d.toString}")
      if(d == true) {
//        RestructuringTaskChooser.heuristic(allTasks).render(globalState)
        DeletePost.render(globalState)
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

case object TaskHeuristic {
  def random(posts: Set[Post], num: Int = 1): Set[Post] = {
    assert(num <= posts.size, "Cannot pick more elements than there are")
    assert(posts.nonEmpty, "Post must not be empty")

    val choice = scala.util.Random.shuffle(posts.toList).take(num).toSet

    assert(choice.nonEmpty, "At least one element must be chosen")

    choice
  }
//  def newest(posts: List[Post], num: Int = 1): List[Post] = {
//    val post = posts.head
//    if(num > 1) List(post) ++ random(posts, num - 1)
//    else List(post)
//  }
//  def gaussTime(posts: List[Post], num: Int = 1): List[Post] = {
//    val post = posts(scala.util.Random.nextInt(posts.size))
//    if(num > 1) List(post) ++ random(posts, num - 1)
//    else List(post)
//  }

  def heuristic: (Set[Post], Int) => Set[Post] = random
}

case object RestructuringTaskChooser {
  def random(tasks: List[RestructuringTask]): RestructuringTask = {
    tasks(scala.util.Random.nextInt(tasks.size))
  }

  def heuristic: List[RestructuringTask] => RestructuringTask = random
}

package wust.frontend.views

import scala.collection.breakOut
import monix.execution.Scheduler.Implicits.global
import org.scalajs.dom.raw.MouseEvent
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend.{Client, GlobalState}
import wust.graph.{Connection, GraphChanges, Post}
import wust.ids.Label

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
//      cursor.pointer // TODO: What about cursor when selecting text?
    ),
  )
}

sealed trait RestructuringTask {
  val title: String
  val description: String
  def component(state: GlobalState): VNode

  def currentPosts(state: GlobalState) = state.inner.displayGraphWithoutParents.now.graph.posts.toSet

  def applyTaskGraphChanges(graphChanges: GraphChanges) = {
    val changes = List(graphChanges)
    Client.api.changeGraph(changes).foreach(res => println("Api call succeeded: " + res.toString))
  }

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

sealed trait YesNoTask extends RestructuringTask
{
  val answerYes = Handler.create[MouseEvent]
  def constructComponent(state: GlobalState,
    postChoice: Set[Post],
    graphChangesYes: GraphChanges): VNode = {
      div(
        postChoice.map(Style.post(_))(breakOut): Seq[VNode],
        div(
          button("Yes",
            onClick(answerYes) --> sideEffect(applyTaskGraphChanges(graphChangesYes)),
            onClick(false) --> RestructuringTaskGenerator.taskDisplay,
          ),
          button("No", onClick(false) --> RestructuringTaskGenerator.taskDisplay),
          width := "100%",
        )
      )
  }
}

sealed trait AddTagTask extends RestructuringTask
{
  val addTag = Handler.create[MouseEvent]

  def constructComponent(state: GlobalState,
    postChoice: Set[Post],
    graphChangesYes: GraphChanges): VNode = {
      div(
      )
  }
}

// Multiple Post RestructuringTask
case object ConnectPosts extends YesNoTask
{
  val title = "Connect Posts"
  val description = "Do these posts belong together?"

  def component(state: GlobalState): VNode = {
    val connectPosts = TaskHeuristic.random(currentPosts(state), 2)
    constructComponent(state,
      connectPosts,
      GraphChanges(addConnections = Set(Connection(connectPosts.head.id, "related", connectPosts.last.id)))
    )
  }
}
case object ContainPosts extends YesNoTask
{
  val title = "Contain Posts"
  val description = "Is the first post a topic description of the second?"

  def component(state: GlobalState): VNode = {
    val containmentPosts = TaskHeuristic.random(currentPosts(state), 2)
    constructComponent(state,
      containmentPosts,
      GraphChanges(addConnections = Set(Connection(containmentPosts.last.id, Label.parent, containmentPosts.head.id)))
    )
  }
}
case object MergePosts extends YesNoTask
{
  val title = "Merge Posts"
  val description = "Does these posts state the same but in different words? If yes, they will be merged."

  def mergePosts(mergeTarget: Post, post: Post): Post = {
    mergeTarget.copy(content = mergeTarget.content + post.content)
  }

  def component(state: GlobalState): VNode = {
    val postsToMerge = TaskHeuristic.random(currentPosts(state), 2)
    constructComponent(state,
      postsToMerge,
      GraphChanges(updatePosts = Set(mergePosts(postsToMerge.head, postsToMerge.last)))
    )
  }
}
case object UnifyPosts extends YesNoTask // Currently same as MergePosts
{
  val title = "Unify Posts"
  val description = "Does these posts state the same and are redundant? If yes, they will be unified."

  def unifyPosts(unifyTarget: Post, post: Post): Post = {
    unifyTarget.copy(content = unifyTarget.content + post.content)
  }

  def component(state: GlobalState): VNode = {
    val postsToUnify = TaskHeuristic.random(currentPosts(state), 2)
    constructComponent(state,
      postsToUnify,
      GraphChanges(updatePosts = Set(unifyPosts(postsToUnify.head, postsToUnify.last)))
    )
  }
}

// Single Post RestructuringTask
case object DeletePost extends YesNoTask
{
  val title = "Delete Post"
  val description = "Is this posts irrelevant for this discussion? (e.g. Hello post)"

  def component(state: GlobalState): VNode = {
    val deletePosts = TaskHeuristic.random(currentPosts(state), 1)
    constructComponent(state, deletePosts, GraphChanges(delPosts = deletePosts.map(_.id)))
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

case object AddTagToPost extends AddTagTask
{
  val title = "Add tag to post"
  val description = "How would you describe this post? Please add a tag."
  def component(state: GlobalState): VNode = {
    div()
  }
}

case object AddTagToConnection extends AddTagTask
{
  val title = "Add tag to connection"
  val description = "How would you describe the relation between these posts? Please add a tag to the relation."
  def component(state: GlobalState): VNode = {
    div()
  }
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

case object RestructuringTaskGenerator {
  val allTasks: List[RestructuringTask] = List(ConnectPosts, ContainPosts, MergePosts, UnifyPosts, DeletePost, SplitPost , AddTagToPost, AddTagToConnection)
  val workingTasks: List[RestructuringTask] = List(ConnectPosts, ContainPosts, MergePosts, DeletePost)

  def apply(globalState: GlobalState) = {
    val show = taskDisplay.map(d => {
      println(s"display task! ${d.toString}")
      if(d == true) {
//        RestructuringTaskChooser.heuristic(allTasks).render(globalState)
        RestructuringTaskChooser.heuristic(workingTasks).render(globalState)
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

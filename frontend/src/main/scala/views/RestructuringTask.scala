package wust.frontend.views

import wust.util.outwatchHelpers._
import wust.frontend.DevPrintln
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, console, window}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend.{Client, EventProcessor, GlobalState}
import wust.frontend.views.Elements._
import wust.frontend.views.PostHeuristic._
import wust.frontend.views.Restructure.Posts
import wust.graph.{Connection, Graph, GraphChanges, Post}
import wust.ids._

import scala.collection.breakOut
import scala.concurrent.Future

object Restructure {
  type Posts = List[Post]
}

sealed trait RestructuringTaskObject {
  type StrategyResult = Future[List[RestructuringTask]]

  val measureBoundary: Double
  val postNumber: Option[Int]

  def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): RestructuringTask

  def applyWithStrategy(graph: Graph, heuristic: PostHeuristicType): StrategyResult = {
    applyWithStrategy(graph, heuristic, postNumber, measureBoundary)
  }
  def applyWithStrategy(graph: Graph, heuristic: PostHeuristicType, num: Option[Int]): StrategyResult = {
    applyWithStrategy(graph, heuristic, num, measureBoundary)
  }

  // Here, the task should choose best fitting posts
  def applyStrategically(graph: Graph, num: Option[Int]): StrategyResult = {
    applyWithStrategy(graph, taskHeuristic, num)
  }

  def applyStrategically(graph: Graph): StrategyResult = {
    applyWithStrategy(graph, taskHeuristic, postNumber)
  }

  def applyWithStrategy(graph: Graph, heuristic: PostHeuristicType, num: Option[Int], measureBoundary: Double): StrategyResult = {
    val futureTask: Future[List[RestructuringTask]] = heuristic(graph, num).map(taskList => taskList.takeWhile(res => res.measure match {
      case None => true
      case Some(measure) => measure > measureBoundary
      case _ => false
    }).map( res => apply(res.posts) ))
    futureTask
  }
}
sealed trait RestructuringTask {

  val title: String
  val description: String
  def component(state: GlobalState): VNode

  def getGraphFromState(state: GlobalState): Graph = state.inner.displayGraphWithParents.now.graph

  def transferChildrenAndParents(graph: Graph, source: PostId, target: PostId): Set[Connection] = {
    val sourceChildren = graph.getChildren(source)
    val sourceParents = graph.getParents(source)
    val childrenConnections = sourceChildren.map(child => Connection(child, Label.parent, target))
    val parentConnections = sourceParents.map(parent => Connection(target, Label.parent, parent))

    childrenConnections ++ parentConnections
  }

  def stylePost(post: Post): VNode = p(
      post.content,
      color.black,
      backgroundColor := "#eee",
      padding := "5px 10px",
      borderRadius := "7px",
      border := "1px solid gray",
      margin := "5px 0px",
  )

  def render(state: GlobalState): VNode = {
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
  def constructComponent(state: GlobalState,
    postChoice: List[Post],
    graphChangesYes: GraphChanges): VNode = {
      div(
        postChoice.map(stylePost)(breakOut): List[VNode],
        div(
          button("Yes",
            onClick(graphChangesYes) --> state.eventProcessor.enriched.changes,
            onClick(true) --> RestructuringTaskGenerator.taskDisplay,
          ),
          button("No", onClick(true) --> RestructuringTaskGenerator.taskDisplay),
          width := "100%",
        )
      )
  }
}

sealed trait AddTagTask extends RestructuringTask
{
  def constructComponent(sourcePosts: List[Post], targetPosts: List[Post], sink: Sink[String]): VNode = {
    div(
      sourcePosts.map(stylePost)(breakOut): List[VNode],
      targetPosts.map(stylePost)(breakOut): List[VNode],
      div(
        textAreaWithEnter(sink)(
          Placeholders.newTag,
          flex := "0 0 3em",
        ),
        button("Abort", onClick(true) --> RestructuringTaskGenerator.taskDisplay),
        width := "100%",
      )
    )
  }
  def constructComponent(sourcePosts: List[Post], sink: Sink[String]): VNode = {
    constructComponent(sourcePosts, List.empty[Post], sink)
  }
}

// Multiple Post RestructuringTask
object ConnectPosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(2)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): ConnectPosts = new ConnectPosts(posts)
}
case class ConnectPosts(posts: Posts) extends YesNoTask
{
  val title = "Connect Posts"
  val description = "Is the first post related to the other posts?"

  def constructGraphChanges(posts: Posts): GraphChanges = {
    val source = posts.head
    val connectionsToAdd = for {
      t <- posts.drop(1) if t.id != source.id
    } yield {
        Connection(source.id, "related", t.id)
      }
    GraphChanges(addConnections = connectionsToAdd.toSet)
  }

  def component(state: GlobalState): VNode = {
    constructComponent(state,
      posts,
      constructGraphChanges(posts)
    )
  }
}

object ConnectPostsWithTag extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(2)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): ConnectPostsWithTag = new ConnectPostsWithTag(posts)
}
case class ConnectPostsWithTag(posts: Posts) extends AddTagTask
{
  val title = "Connect Posts with tag"
  val description = "How would you describe the relation between the first post and the others? Tag it!"

  def tagConnection(sourcePosts: List[Post],
    targetPosts: List[Post],
    state: GlobalState): Sink[String] = {
    state.eventProcessor.changes.redirectMap { (tag: String) =>
      val tagConnections = for {
        s <- sourcePosts
        t <- targetPosts if s.id != t.id
      } yield {
        Connection(s.id, tag, t.id)
      }

      RestructuringTaskGenerator.taskDisplay.unsafeOnNext(true)

      GraphChanges(
        addConnections = tagConnections.toSet
      )
    }
  }

  def component(state: GlobalState): VNode = {
    val sourcePosts = posts.take(1)
    val targetPosts = posts.slice(1, 2)

    constructComponent(sourcePosts, targetPosts, tagConnection(sourcePosts, targetPosts, state))
  }
}

object ContainPosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(2)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): ContainPosts = new ContainPosts(posts)
}
case class ContainPosts(posts: Posts) extends YesNoTask
{
  val title = "Contain Posts"
  val description = "Is the first post a topic description of the others?"

  def constructGraphChanges(posts: Posts): GraphChanges = {
    val target = posts.head
    val containmentsToAdd = for {
      s <- posts.drop(1) if s.id != target.id
    } yield {
        Connection(s.id, Label.parent, target.id)
    }

    GraphChanges(addConnections = containmentsToAdd.toSet)
  }

  def component(state: GlobalState): VNode = {
    constructComponent(state,
      posts,
      constructGraphChanges(posts)
    )
  }
}

object MergePosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(2)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): MergePosts = new MergePosts(posts)
}
case class MergePosts(posts: Posts) extends YesNoTask
{
  val title = "Merge Posts"
  val description = "Does these posts state the same but in different words? If yes, they will be merged."

  def constructGraphChanges(graph: Graph, posts: Posts): GraphChanges = {
    val target = posts.head
    val postsToDelete = posts.drop(1)
    val postsToUpdate = for {
      source <- postsToDelete if source.id != target.id
    } yield {
      (mergePosts(target, source), transferChildrenAndParents(graph, source.id, target.id))
    }

    GraphChanges(
      addConnections = postsToUpdate.flatMap(_._2).toSet,
      updatePosts = postsToUpdate.map(_._1).toSet,
      delPosts = postsToDelete.map(_.id).toSet)
  }

  def mergePosts(mergeTarget: Post, source: Post): Post = {
    mergeTarget.copy(content = mergeTarget.content + "\n" + source.content)
  }

  def component(state: GlobalState): VNode = {
    constructComponent(state,
      posts,
      constructGraphChanges(getGraphFromState(state), posts)
    )
  }
}

object UnifyPosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(2)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): UnifyPosts = new UnifyPosts(posts)
}
case class UnifyPosts(posts: Posts) extends YesNoTask // Currently same as MergePosts
{
  val title = "Unify Posts"
  val description = "Does these posts state the same and are redundant? If yes, they will be unified."

  def constructGraphChanges(graph: Graph, posts: Posts): GraphChanges = {
    val target = posts.head
    val postsToDelete = posts.drop(1)
    val postsToUpdate = for {
      source <- posts.drop(1) if source.id != target.id
    } yield {
      (unifyPosts(target, source), transferChildrenAndParents(graph, source.id, target.id))
    }

    GraphChanges(
      addConnections = postsToUpdate.flatMap(_._2).toSet,
      updatePosts = postsToUpdate.map(_._1).toSet,
      delPosts = postsToDelete.map(_.id).toSet)
  }

  def unifyPosts(unifyTarget: Post, post: Post): Post = {
    unifyTarget.copy(content = unifyTarget.content + "\n" + post.content)
  }

  def component(state: GlobalState): VNode = {
    constructComponent(state,
      posts,
      constructGraphChanges(getGraphFromState(state), posts)
    )
  }
}

// Single Post RestructuringTask
object DeletePosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(1)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): DeletePosts = new DeletePosts(posts)
}
case class DeletePosts(posts: Posts) extends YesNoTask
{
  val title = "Delete Post"
  val description = "Is this posts irrelevant for this discussion? (e.g. Hello post)"

  def component(state: GlobalState): VNode = {
    constructComponent(state,
      posts,
      GraphChanges(delPosts = posts.map(_.id).toSet)
    )
  }
}

object SplitPosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(1)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): SplitPosts = new SplitPosts(posts)
}
case class SplitPosts(posts: Posts) extends RestructuringTask
{
  val title = "Split Post"
  val description = "Does this Post contain multiple statements? Please split the post. You can split a part of this post by selecting it and confirm the selectio with the button."

  def stringToPost(str: String, condition: Boolean, state: GlobalState): Option[Post] = {
    if(!condition) return None
    Some(Post(PostId.fresh, str.trim, state.inner.currentUser.now.id))
  }

  def splittedPostPreview(event: MouseEvent, originalPost: Post, state: GlobalState): List[Post] = {
    val selection = window.getSelection()
    if(selection.rangeCount > 1)// what about multiple Selections?
      return List(originalPost)

    val range = selection.getRangeAt(0)
    val selectionOffsets = (range.startOffset, range.endOffset)

    val elementText = event.currentTarget.asInstanceOf[dom.html.Paragraph].textContent
    val currSelText = elementText.substring(selectionOffsets._1, selectionOffsets._2).trim

    val before = stringToPost(elementText.take(selectionOffsets._1), selectionOffsets._1 != 0, state)
    val middle = stringToPost(currSelText, currSelText.nonEmpty, state)
    val after = stringToPost(elementText.substring(selectionOffsets._2), selectionOffsets._2 != elementText.length, state)

    console.log(s"currSelection: $selectionOffsets")
    console.log(s"currSelText: $currSelText")

    List(before, middle, after).flatten
  }

  def generateGraphChanges(originalPosts: List[Post], posts: List[Post], graph: Graph): GraphChanges = {

    val keepRelatives = originalPosts.flatMap(originalPost => posts.flatMap(p => transferChildrenAndParents(graph, originalPost.id, p.id))).toSet
    val newConnections = originalPosts.flatMap(originalPost => posts.map(p => Connection(p.id, "splitFrom", originalPost.id))).toSet
    val newPosts = originalPosts.flatMap(originalPost => posts.filter(_.id != originalPost.id)).toSet
    GraphChanges(
      addPosts = newPosts,
      addConnections = newConnections ++ keepRelatives,
      delPosts = originalPosts.map(_.id).toSet,
    )
  }

  def component(state: GlobalState): VNode = {
    val splitPost = posts.take(1)
    val postPreview = Handler.create[List[Post]](splitPost).unsafeRunSync()

    div(
      div(
        children <-- postPreview.map {posts =>
          posts.map {post =>
            p(
              post.content,
              color.black,
              maxWidth := "60%",
              backgroundColor := "#eee",
              padding := "5px 10px",
              borderRadius := "7px",
              border := "1px solid gray",
              margin := "5px 0px",
              onMouseUp.map(e => posts.flatMap(p => if(p == post) splittedPostPreview(e, post, state) else List(p))) --> postPreview,
            )
          }
        },
        width := "100%",
      ),
      button("Confirm",
        onClick(postPreview).map(generateGraphChanges(splitPost, _, getGraphFromState(state))) --> state.eventProcessor.enriched.changes,
        onClick(true) --> RestructuringTaskGenerator.taskDisplay,
      ),
      button("Abort",
        onClick(true) --> RestructuringTaskGenerator.taskDisplay,
      ),
    )
  }
}

object AddTagToPosts extends RestructuringTaskObject {
  val measureBoundary = 0.5
  val postNumber = Some(1)

  override def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): AddTagToPosts = new AddTagToPosts(posts)
}
case class AddTagToPosts(posts: Posts) extends AddTagTask
{
  val title = "Add tag to post"
  val description = "How would you describe this post? Please add a tag."

  def addTagToPost(post: List[Post], state: GlobalState): Sink[String] = {

    state.eventProcessor.changes.redirectMap { (tag: String) =>
      val tagPost = state.inner.rawGraph.now.posts.find(_.content == tag).getOrElse(Post(PostId.fresh, tag, state.inner.currentUser.now.id))
      val tagConnections = post.map(p => Connection(p.id, Label.parent, tagPost.id))

      RestructuringTaskGenerator.taskDisplay.unsafeOnNext(true)

      GraphChanges(
        addPosts = Set(tagPost),
        addConnections = tagConnections.toSet
      )
    }
  }

  def component(state: GlobalState): VNode = {
    val postsToTag = posts
    constructComponent(postsToTag, addTagToPost(postsToTag, state))
  }
}

case object RestructuringTaskGenerator {

  val allTasks: List[RestructuringTaskObject] = List(
    ConnectPosts,
    ConnectPostsWithTag,
    ContainPosts,
    MergePosts,
    UnifyPosts,
    DeletePosts,
    SplitPosts,
    AddTagToPosts,
  )

  val taskDisplay: Handler[Boolean] = Handler.create[Boolean](false).unsafeRunSync()

  def apply(globalState: GlobalState): Observable[VNode] = {

    def renderButton: VNode = div(
      span("Tasks"),
      fontWeight.bold,
      fontSize := "20px",
      marginBottom := "10px",
      button("Task me!", width := "100%", onClick(true) --> taskDisplay),
    )

    val show = taskDisplay.map(d => {
      DevPrintln(s"display task! ${d.toString}")
      if(d) {
        div(
          children <-- Observable.fromFuture(composeTask(globalState).map(_.map(_.render(globalState))))
        )

      } else {
          renderButton,
      }
    })

    show
  }

  // Currently, we choose a task at random and decide afterwards which heuristic is used
  // But it is also possible to decide a task based on given post metrics
  // Therefore if we can find / define metrics of a discussion, we can choose an
  // applicable task after choosing a post (based on a discussion metric)
  def composeTask(globalState: GlobalState): Future[List[RestructuringTask]] = {

    val graph = globalState.inner.displayGraphWithoutParents.now.graph
    val task = ChooseTaskHeuristic.random(allTasks)
    task.applyStrategically(graph)

    // TODO: Different strategies based on choosing order
//    val finalTask = if(scala.util.Random.nextBoolean) { // First task, then posts
//      val task = ChooseTaskHeuristic.random(allTasks)
//      task.applyStrategically(discussionPosts)
//    } else {// First posts, then task
//      // TODO: Choose tasks strategically based on metrics
//      val posts = ChoosePostHeuristic.random(discussionPosts)
//      val task = ChooseTaskHeuristic.random(allTasks)
//      task(posts)
//    }
//    finalTask
  }

}

package wust.frontend.views

import monix.execution.Scheduler.Implicits.global
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, window, console}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend.{Client, GlobalState, EventProcessor}
import wust.frontend.views.Elements._
import wust.frontend.views.Heuristic._
import wust.graph.{Connection, GraphChanges, Post}
import wust.ids._

import scala.collection.breakOut

sealed trait RestructuringTask {

  val title: String
  val description: String
  def component(state: GlobalState): VNode

  def currentPosts(state: GlobalState) = state.inner.displayGraphWithoutParents.now.graph.posts.toSet

  def stylePost(post: Post) = p(
      post.content,
      color.black,
      maxWidth := "60%",
      backgroundColor := "#eee",
      padding := "5px 10px",
      borderRadius := "7px",
      border := "1px solid gray",
      margin := "5px 0px",
  )

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
  def constructComponent(state: GlobalState,
    postChoice: Set[Post],
    graphChangesYes: GraphChanges): VNode = {
      div(
        postChoice.map(stylePost(_))(breakOut): Seq[VNode],
        div(
          button("Yes",
            onClick(graphChangesYes) --> state.eventProcessor.enriched.changes,
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
  def constructComponent(sourcePosts: Set[Post], targetPosts: Set[Post], sink: Sink[String]): VNode = {
    div(
      sourcePosts.map(stylePost(_))(breakOut): Seq[VNode],
      targetPosts.map(stylePost(_))(breakOut): Seq[VNode],
      div(
        textAreaWithEnter(sink)(
          Placeholders.newTag,
          flex := "0 0 3em",
        ),
        button("Abort", onClick(false) --> RestructuringTaskGenerator.taskDisplay),
        width := "100%",
      )
    )
  }
  def constructComponent(sourcePosts: Set[Post], sink: Sink[String]): VNode = {
    constructComponent(sourcePosts, Set.empty[Post], sink)
  }
}

// Multiple Post RestructuringTask
case class ConnectPosts(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends YesNoTask
{
  val title = "Connect Posts"
  val description = "Do these posts belong together?"

  def component(state: GlobalState): VNode = {
    val connectPosts = heuristic(currentPosts(state), 2)
    constructComponent(state,
      connectPosts,
      GraphChanges(addConnections = Set(Connection(connectPosts.head.id, "related", connectPosts.last.id)))
    )
  }
}

case class ConnectPostsWithTag(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends AddTagTask
{
  val title = "Connect Posts with tag"
  val description = "How would you describe the relation between these posts? Tag it!"

  def tagConnection(sourcePosts: Set[Post],
    targetPosts: Set[Post],
    state: GlobalState): Sink[String] =
      state.eventProcessor.changes.redirectMap { (tag: String) =>
        val tagConnections: Set[Connection] = for(s <- sourcePosts; t <- targetPosts) yield {
          Connection(s.id, tag, t.id)
        }
        RestructuringTaskGenerator.taskDisplay.unsafeOnNext(false)
        GraphChanges(
          addConnections = tagConnections
        )
      }

  def component(state: GlobalState): VNode = {
    val sourcePosts = heuristic(currentPosts(state), 1)
    val targetPosts = heuristic(currentPosts(state), 1)
    constructComponent(sourcePosts, targetPosts, tagConnection(sourcePosts, targetPosts, state))
  }
}

case class ContainPosts(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends YesNoTask
{
  val title = "Contain Posts"
  val description = "Is the first post a topic description of the second?"

  def component(state: GlobalState): VNode = {
    val containmentPosts = heuristic(currentPosts(state), 2)
    constructComponent(state,
      containmentPosts,
      GraphChanges(addConnections = Set(Connection(containmentPosts.last.id, Label.parent, containmentPosts.head.id)))
    )
  }
}

case class MergePosts(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends YesNoTask
{
  val title = "Merge Posts"
  val description = "Does these posts state the same but in different words? If yes, they will be merged."

  def mergePosts(mergeTarget: Post, post: Post): Post = {
    mergeTarget.copy(content = mergeTarget.content + "\n" + post.content)
  }

  def component(state: GlobalState): VNode = {
    val postsToMerge = heuristic(currentPosts(state), 2)
    constructComponent(state,
      postsToMerge,
      GraphChanges(updatePosts = Set(mergePosts(postsToMerge.head, postsToMerge.last)))
    )
  }
}

case class UnifyPosts(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends YesNoTask // Currently same as MergePosts
{
  val title = "Unify Posts"
  val description = "Does these posts state the same and are redundant? If yes, they will be unified."

  def unifyPosts(unifyTarget: Post, post: Post): Post = {
    unifyTarget.copy(content = unifyTarget.content + "\n" + post.content)
  }

  def component(state: GlobalState): VNode = {
    val postsToUnify = heuristic(currentPosts(state), 2)
    constructComponent(state,
      postsToUnify,
      GraphChanges(updatePosts = Set(unifyPosts(postsToUnify.head, postsToUnify.last)))
    )
  }
}

// Single Post RestructuringTask
case class DeletePost(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends YesNoTask
{
  val title = "Delete Post"
  val description = "Is this posts irrelevant for this discussion? (e.g. Hello post)"

  def component(state: GlobalState): VNode = {
    val deletePosts = heuristic(currentPosts(state), 1)
    constructComponent(state,
      deletePosts,
      GraphChanges(delPosts = deletePosts.map(_.id))
    )
  }
}

case class SplitPost(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends RestructuringTask
{
  val title = "Split Post"
  val description = "Does this Post contain multiple statements? Please split the post. You can split a part of this post by selecting it and confirm the selectio with the button."

  def stringToPost(str: String, condition: Boolean, state: GlobalState): Option[Post] = {
    if(!condition) return None
    Some(Post(PostId.fresh, str.trim, state.inner.currentUser.now))
  }

  def splittedPostPreview(event: MouseEvent, originalPost: Post, state: GlobalState): Seq[Post] = {
    val selection = window.getSelection()
    if(selection.rangeCount > 1)// what about multiple Selections?
      return Seq(originalPost)

    val range = selection.getRangeAt(0)
    val selectionOffsets = (range.startOffset, range.endOffset)

    val elementText = event.currentTarget.asInstanceOf[dom.html.Paragraph].textContent
    val currSelText = elementText.substring(selectionOffsets._1, selectionOffsets._2).trim

    val before = stringToPost(elementText.take(selectionOffsets._1), selectionOffsets._1 != 0, state)
    val middle = stringToPost(currSelText, currSelText.nonEmpty, state)
    val after = stringToPost(elementText.substring(selectionOffsets._2), selectionOffsets._2 != elementText.length, state)

    console.log(s"currSelection: $selectionOffsets")
    console.log(s"currSelText: $currSelText")

    Seq(before, middle, after).flatten
  }

  def generateGraphChanges(originalPost: Post, posts: Seq[Post], state: GlobalState): GraphChanges = {
    val connections = posts.map(p => Connection(p.id ,"splitFrom", originalPost.id)).toSet
    GraphChanges(
      addPosts = posts.filter(_.id != originalPost.id).toSet,
      addConnections = connections,
      delPosts = Set(originalPost.id),
    )
  }

  def component(state: GlobalState): VNode = {
    val splitPost = heuristic(currentPosts(state), 1).head
    val postPreview = Handler.create[Seq[Post]](Seq(splitPost)).unsafeRunSync()

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
              onMouseUp.map(e => posts.flatMap(p => if(p == post) splittedPostPreview(e, post, state) else Seq(p))) --> postPreview,
            )
          }
        },
        width := "100%",
      ),
      button("Confirm",
        onClick(postPreview).map(generateGraphChanges(splitPost, _, state)) --> state.eventProcessor.enriched.changes,
        onClick(false) --> RestructuringTaskGenerator.taskDisplay,
      ),
      button("Abort",
        onClick(false) --> RestructuringTaskGenerator.taskDisplay,
      ),
    )
  }
}

case class AddTagToPost(heuristic: PostHeuristic = ChoosePostHeuristic.defaultHeuristic) extends AddTagTask
{
  val title = "Add tag to post"
  val description = "How would you describe this post? Please add a tag."

  def addTagToPost(post: Set[Post], state: GlobalState): Sink[String] =
    state.eventProcessor.changes.redirectMap { (tag: String) =>
      val tagPost = state.inner.rawGraph.now.posts.find(_.content == tag).getOrElse(Post(PostId.fresh, tag, state.inner.currentUser.now))
      val tagConnections = post.map(p => Connection(p.id, Label.parent, tagPost.id))
      RestructuringTaskGenerator.taskDisplay.unsafeOnNext(false)
      GraphChanges(
        addPosts = Set(tagPost),
        addConnections = tagConnections
      )
    }

  def component(state: GlobalState): VNode = {
    val postsToTag = heuristic(currentPosts(state), 1)
    constructComponent(postsToTag, addTagToPost(postsToTag, state))
  }
}

case object RestructuringTaskGenerator {
  
  val commonHeuristic = ChoosePostHeuristic.defaultHeuristic
  
  val allTasks: List[RestructuringTask] = List(
    ConnectPosts(commonHeuristic),
    ConnectPostsWithTag(commonHeuristic),
    ContainPosts(commonHeuristic),
    MergePosts(commonHeuristic),
    UnifyPosts(commonHeuristic),
    DeletePost(commonHeuristic),
    SplitPost(commonHeuristic),
    AddTagToPost(commonHeuristic),
  )

  def apply(globalState: GlobalState) = {
    val show = taskDisplay.map(d => {
      println(s"display task! ${d.toString}")
      if(d == true) {
        ChooseTaskHeuristic.defaultHeuristic(allTasks).render(globalState)
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

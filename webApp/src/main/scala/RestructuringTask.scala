package wust.webApp

import wust.util.outwatchHelpers._
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, console, window}
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp.views.Elements._
import wust.webApp.PostHeuristic._
import wust.webApp.Restructure._
import wust.graph.{Connection, Graph, GraphChanges, Post}
import wust.ids._

import scala.collection.breakOut
import scala.concurrent.{Future, Promise}

object Restructure {
  type Posts = List[Post]
  type Probability = Double
  case class HeuristicParameters(probability: Probability, heuristic: PostHeuristicType = PostHeuristic.Random.heuristic, measureBoundary: Option[Double] = None, numMaxPosts: Option[Int] = None)
  case class TaskFeedback(displayNext: Boolean, taskAnswer: GraphChanges)
}

sealed trait RestructuringTaskObject {
  type StrategyResult = Future[List[RestructuringTask]]

  val measureBoundary: Option[Double]
  val numMaxPosts: Option[Int]

  def taskHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic
  def fallbackHeuristic: PostHeuristicType = PostHeuristic.Random.heuristic

  def apply(posts: Posts): RestructuringTask

  def applyWithStrategy(graph: Graph, heuristic: PostHeuristicType): StrategyResult = {
    applyWithStrategy(graph, heuristic, numMaxPosts, measureBoundary)
  }
  def applyWithStrategy(graph: Graph, heuristic: PostHeuristicType, num: Option[Int]): StrategyResult = {
    applyWithStrategy(graph, heuristic, num, measureBoundary)
  }

  // Here, the task should choose best fitting posts
  def applyStrategically(graph: Graph, numMaxPosts: Option[Int]): StrategyResult = {
    applyWithStrategy(graph, taskHeuristic, numMaxPosts)
  }

  def applyStrategically(graph: Graph): StrategyResult = {
    applyWithStrategy(graph, taskHeuristic, numMaxPosts)
  }

  def applyWithStrategy(graph: Graph, heuristic: PostHeuristicType, numMaxPosts: Option[Int], measureBoundary: Option[Double]): StrategyResult = {
    val futurePosts = heuristic(graph, numMaxPosts)
      .map( taskList => taskList.takeWhile(res => res.measure match {
        case None => true
        case Some(measure) => measure > measureBoundary.getOrElse(0.0)
        case _ => false
      }))

    val futureTask: Future[List[RestructuringTask]] =  for {
      strategyPosts <- futurePosts
      finalPosts <- if(strategyPosts.nonEmpty) Future.successful(strategyPosts) else fallbackHeuristic(graph, numMaxPosts)
    } yield finalPosts.map(res => apply(res.posts))

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
              onClick(TaskFeedback(false, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
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
          onClick(graphChangesYes) --> ObserverSink(state.eventProcessor.enriched.changes),
          onClick(TaskFeedback(true, graphChangesYes)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
        ),
        button("No", onClick(TaskFeedback(true, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging),
        width := "100%",
      )
    )
  }

  def constructComponent(state: GlobalState,
                         postDisplay: VNode,
                         graphChangesYes: GraphChanges): VNode = {
    div(
      postDisplay,
      div(
        button("Yes",
          onClick(graphChangesYes) --> ObserverSink(state.eventProcessor.enriched.changes),
          onClick(TaskFeedback(true, graphChangesYes)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
        ),
        button("No", onClick(TaskFeedback(true, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging),
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
          views.Placeholders.newTag,
          flex := "0 0 3em",
        ),
        button("Abort", onClick(TaskFeedback(true, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging),
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
  private val defaultMeasureBoundary = Some(0.5)
  private val defaultNumMaxPosts = Some(2)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(2,  PostHeuristic.Jaccard(2).heuristic,       defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.DiceSorensen(2).heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.Random.heuristic,           None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic,       None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,        None,                   defaultNumMaxPosts)
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

  def apply(posts: Posts): ConnectPosts = new ConnectPosts(posts)
}
case class ConnectPosts(posts: Posts) extends YesNoTask
{
  val title = "Connect posts"
  val description = "Is the first post related to the other post(s)?"

  def constructGraphChanges(posts: Posts): GraphChanges = {
    val source = posts.head
    val connectionsToAdd = for {
      t <- posts.drop(1) if t.id != source.id
    } yield {
        Connection(source.id, Label("related"), t.id)
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
  private val defaultMeasureBoundary = Some(0.5)
  private val defaultNumMaxPosts = Some(2)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(2,  PostHeuristic.Jaccard(2).heuristic,       defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.DiceSorensen(2).heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.Random.heuristic,           None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic,       None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,        None,                   defaultNumMaxPosts)
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

  def apply(posts: Posts): ConnectPostsWithTag = new ConnectPostsWithTag(posts)
}
case class ConnectPostsWithTag(posts: Posts) extends AddTagTask
{
  val title = "Connect posts with tag"
  val description = "How would you describe the relation between the first post and the second / others? Tag it!"

  def tagConnection(sourcePosts: List[Post],
    targetPosts: List[Post],
    state: GlobalState): Sink[String] = {
    ObserverSink(state.eventProcessor.changes).redirectMap { (tag: String) =>
      val tagConnections = for {
        s <- sourcePosts
        t <- targetPosts if s.id != t.id
      } yield {
        Connection(s.id, Label(tag), t.id)
      }

      val changes = GraphChanges(
        addConnections = tagConnections.toSet
      )

      RestructuringTaskGenerator.taskDisplayWithLogging.unsafeOnNext(TaskFeedback(true, changes))

      changes
    }
  }

  def component(state: GlobalState): VNode = {
    val sourcePosts = posts.take(1)
    val targetPosts = posts.slice(1, 2)

    constructComponent(sourcePosts, targetPosts, tagConnection(sourcePosts, targetPosts, state))
  }
}

object ContainPosts extends RestructuringTaskObject {
  private val defaultMeasureBoundary = None
  private val defaultNumMaxPosts = Some(2)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(1,  PostHeuristic.Jaccard(2).heuristic,       defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.DiceSorensen(2).heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(3,  PostHeuristic.Random.heuristic,           None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic,       None,                   defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.GaussTime.heuristic,        None,                   defaultNumMaxPosts)
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

  def apply(posts: Posts): ContainPosts = new ContainPosts(posts)
}
case class ContainPosts(posts: Posts) extends YesNoTask
{
  val title = "Contain Posts"
  val description = "Is the first post a topic description of the second / others?"

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
  private val defaultMeasureBoundary = Some(0.9)
  private val defaultNumMaxPosts = Some(2)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(2,  PostHeuristic.Jaccard(2).heuristic,       defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.DiceSorensen(2).heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.Random.heuristic,           None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic,       None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,        None,                   defaultNumMaxPosts)
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

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
  private val defaultMeasureBoundary = Some(0.9)
  private val defaultNumMaxPosts = Some(2)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(2,  PostHeuristic.Jaccard(2).heuristic,       defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.DiceSorensen(2).heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(2,  PostHeuristic.Random.heuristic,           None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic,       None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,        None,                   defaultNumMaxPosts)
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

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
  private val defaultMeasureBoundary = None
  private val defaultNumMaxPosts = Some(-1)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(1,  PostHeuristic.Random.heuristic,     defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic, None,                   defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,  None,                   defaultNumMaxPosts)
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

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
  private val defaultMeasureBoundary = None
  private val defaultNumMaxPosts = Some(1)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(2,  PostHeuristic.MaxPostSize.heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.Random.heuristic,       defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic,   defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,    defaultMeasureBoundary, defaultNumMaxPosts),
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

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

  def generateGraphChanges(originalPosts: List[Post], previewPosts: List[Post], graph: Graph): GraphChanges = {

    if(previewPosts.isEmpty) return GraphChanges.empty

    val keepRelatives = originalPosts.flatMap(originalPost => previewPosts.flatMap(p => transferChildrenAndParents(graph, originalPost.id, p.id))).toSet
    val newConnections = originalPosts.flatMap(originalPost => previewPosts.map(p => Connection(p.id, Label("splitFrom"), originalPost.id))).toSet
    val newPosts = originalPosts.flatMap(originalPost => previewPosts.filter(_.id != originalPost.id)).toSet
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
        onClick(postPreview).map(generateGraphChanges(splitPost, _, getGraphFromState(state))) --> ObserverSink(state.eventProcessor.enriched.changes),
        onClick(postPreview).map(preview => TaskFeedback(true, generateGraphChanges(splitPost, preview, getGraphFromState(state)))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
      ),
      button("Abort",
        onClick(TaskFeedback(true, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
      ),
    )
  }
}

object AddTagToPosts extends RestructuringTaskObject {
  private val defaultMeasureBoundary = None
  private val defaultNumMaxPosts = Some(1)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(1,  PostHeuristic.Random.heuristic,     defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.GaussTime.heuristic,  defaultMeasureBoundary, defaultNumMaxPosts),
  )

  private val heuristicParam = ChoosePostHeuristic.choose(possibleHeuristics)

  override val measureBoundary: Option[Probability] = heuristicParam.measureBoundary
  override val numMaxPosts: Option[Int] = heuristicParam.numMaxPosts
  override def taskHeuristic: PostHeuristicType = heuristicParam.heuristic

  def apply(posts: Posts): AddTagToPosts = new AddTagToPosts(posts)
}
case class AddTagToPosts(posts: Posts) extends AddTagTask
{
  val title = "Add tag to post"
  val description = "How would you describe this post? Please add a tag."

  def addTagToPost(post: List[Post], state: GlobalState): Sink[String] = {

    ObserverSink(state.eventProcessor.changes).redirectMap { (tag: String) =>
      val tagPost = state.inner.rawGraph.now.posts.find(_.content == tag).getOrElse(Post(PostId.fresh, tag, state.inner.currentUser.now.id))
      val tagConnections = post.map(p => Connection(p.id, Label.parent, tagPost.id))

      val changes = GraphChanges(
        addPosts = Set(tagPost),
        addConnections = tagConnections.toSet
      )

      RestructuringTaskGenerator.taskDisplayWithLogging.unsafeOnNext(TaskFeedback(true, changes))

      changes
    }
  }

  def component(state: GlobalState): VNode = {
    val postsToTag = posts
    constructComponent(postsToTag, addTagToPost(postsToTag, state))
  }
}

case object RestructuringTaskGenerator {

  var _studyTaskIndex: Int = 0
  val studyTaskList = List(
        ConnectPostsWithTag,
        MergePosts,
        SplitPosts,
        AddTagToPosts
  )

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

//  val taskDisplay: Handler[Boolean] = Handler.create[Boolean](false).unsafeRunSync()
  val taskDisplayWithLogging: Handler[TaskFeedback] = Handler.create[TaskFeedback](TaskFeedback(false, GraphChanges.empty)).unsafeRunSync()

//  taskDisplayWithLogging.foreach{ feedback =>
//    val taskTitle = studyTaskList(_studyTaskIndex).toString
//    if(feedback.displayNext && feedback.taskAnswer.nonEmpty) scribe.info(s"RESTRUCTURING TASKS LOG: $taskTitle -> YES -> ${feedback.taskAnswer}")
//    else if(feedback.displayNext && feedback.taskAnswer.isEmpty) scribe.info(s"RESTRUCTURING TASKS LOG: $taskTitle -> NO -> ${feedback.taskAnswer}")
//    else scribe.info(s"RESTRUCTURING TASKS LOG: FINISHED")
//  }

  def apply(globalState: GlobalState): Observable[VNode] = {

    def renderButton(activateTasks: Boolean): VNode = {
      val buttonType = if(activateTasks) {
        button("Close tasks!", width := "100%", onClick(TaskFeedback(displayNext = false, GraphChanges.empty)) --> taskDisplayWithLogging)
      } else {
        button("Task me!", width := "100%", onClick(TaskFeedback(displayNext = true, GraphChanges.empty)) --> taskDisplayWithLogging)
      }
      div(
        span("Tasks"),
        fontWeight.bold,
        fontSize := "20px",
        marginBottom := "10px",
        buttonType
      )
    }

    val show = taskDisplayWithLogging.map{ feedback =>

      DevPrintln(s"display task! ${feedback.toString}")
      if(feedback.displayNext) {
        if(_studyTaskIndex > 0) {
          val taskTitle = studyTaskList(_studyTaskIndex - 1).toString
          if (feedback.taskAnswer.nonEmpty) scribe.info(s"RESTRUCTURING TASKS LOG: $taskTitle -> YES -> ${feedback.taskAnswer}")
          else if (feedback.taskAnswer.isEmpty) scribe.info(s"RESTRUCTURING TASKS LOG: $taskTitle -> NO")
        }
        if(_studyTaskIndex >= studyTaskList.size){
          _studyTaskIndex = 0
          scribe.info("All tasks are finished")
          renderButton(activateTasks = false),
        } else {
          div(
            renderButton(activateTasks = true),
            children <-- Observable.fromFuture(composeStudyTask(globalState).map(_.map(_.render(globalState))))
          )
        }

      } else {
        renderButton(activateTasks = false)
      }
    }

    show
  }

  // Deterministic with predefined task composition
  def composeStudyTask(globalState: GlobalState): Future[List[RestructuringTask]] = {
    val graph = globalState.inner.displayGraphWithoutParents.now.graph
    //    ConnectPostsWithTag.applyWithStrategy(graph, PostHeuristic.Deterministic(List.empty[Post]).heuristic, Some(2), None),
    //    MergePosts.applyWithStrategy(graph, PostHeuristic.Deterministic(List.empty[Post]).heuristic, Some(2), None),
    //    SplitPosts.applyWithStrategy(graph, PostHeuristic.Deterministic(List.empty[Post]).heuristic, Some(1), None),
    //    AddTagToPosts.applyWithStrategy(graph, PostHeuristic.Deterministic(List.empty[Post]).heuristic, Some(1), None)

    // org.scalajs.dom.window.location.href = "Eval?"
    val nextTask = studyTaskList(_studyTaskIndex).applyWithStrategy(graph, PostHeuristic.Deterministic(List.empty[Post]).heuristic)
    _studyTaskIndex = _studyTaskIndex + 1
    nextTask
  }

  // Currently, we choose a task at random and decide afterwards which heuristic is used
  // But it is also possible to decide a task based on given post metrics
  // Therefore if we can find / define metrics of a discussion, we can choose an
  // applicable task after choosing a post (based on a discussion metric)
  def composeTask(globalState: GlobalState): Future[List[RestructuringTask]] = {

    val graph = globalState.inner.displayGraphWithoutParents.now.graph
    val task = ChooseTaskHeuristic.random(allTasks)
    task.applyStrategically(graph)

    //      val graph = globalState.inner.displayGraphWithoutParents.now.graph
    //      val task = ChooseTaskHeuristic.random(allTasks)
    //      task.applyStrategically(graph)

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

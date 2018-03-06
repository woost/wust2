package wust.webApp

import wust.utilWeb.outwatchHelpers._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{MouseEvent, console, window}
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import wust.utilWeb.views.Elements._
import wust.utilWeb.views.Placeholders
import wust.utilWeb.views.Rendered._
import wust.utilWeb._
import wust.webApp.PostHeuristic._
import wust.webApp.Restructure._
import wust.graph.{Connection, Graph, GraphChanges, Post}
import wust.ids._
import wust.utilWeb._

import scala.collection.breakOut
import scala.concurrent.{Future, Promise}

object Restructure {
  type Posts = List[Post]
  type Probability = Double
  type StrategyResult = Future[List[RestructuringTask]]
  case class HeuristicParameters(probability: Probability, heuristic: PostHeuristicType = PostHeuristic.Random.heuristic, measureBoundary: Option[Double] = None, numMaxPosts: Option[Int] = None)
  case class TaskFeedback(displayNext: Boolean, taskAnswer: Boolean, graphChanges: GraphChanges)
}

sealed trait RestructuringTaskObject {
  type StrategyResult = Restructure.StrategyResult

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
  val descriptionEng: String
  val descriptionGer: String
  val positiveText: String
  val negativeText: String
  def description: String = descriptionGer

  def component(state: GlobalState): VNode

  def getGraphFromState(state: GlobalState): Graph = state.inner.displayGraphWithParents.now.graph

  def transferChildrenAndParents(graph: Graph, source: PostId, target: PostId): Set[Connection] = {
    val sourceChildren = graph.getChildren(source)
    val sourceParents = graph.getParents(source)
    val childrenConnections = sourceChildren.map(child => Connection(child, Label.parent, target))
    val parentConnections = sourceParents.map(parent => Connection(target, Label.parent, parent))

    childrenConnections ++ parentConnections
  }

  def arrowDiv(arrowLengthPixel: Int = 90): VNode = div(
    div(
      marginTop := "14px",
      width := s"${arrowLengthPixel}px",
      background := "black",
      height := "10px",
      float.left,
    ),
    div(
      width := "0",
      height:= "0",
      borderTop := "20px solid transparent",
      borderBottom := "20px solid transparent",
      borderLeft := "30px solid black",
      float.right,
    ),
    width := s"${arrowLengthPixel + 30}px",
  )

  def stylePost(post: Post): VNode = p(
      post.content,
      display := "inline-block",
      color.black,
      maxWidth := "80ch",
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
            span(
              "×",
              onClick(TaskFeedback(false, false, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
              cursor.pointer,
              float.right,
              fontSize := "28px",
              fontWeight.bold,
            ),
            h2(s"Kalt duschen"),
            width := "100%",
          ),
          div(
            mdHtml(description),
            borderTop := "2px dotted",
          ),
          div(
            component(state),
            borderTop := "2px dotted",
          ),
          padding := "2px 16px",
        ),
        div(//footer
          padding := "2px 16px",
          backgroundColor := "green",
          color := "black",
        ),
        width := "120ch",
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
        button(positiveText,
          onClick(graphChangesYes) --> ObserverSink(state.eventProcessor.enriched.changes),
          onClick(TaskFeedback(true, true, graphChangesYes)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
          onClick --> sideEffect(scribe.info(s"$title($postChoice) = YES")),
        ),
        button(negativeText,
          onClick(TaskFeedback(true, false, graphChangesYes)) --> RestructuringTaskGenerator.taskDisplayWithLogging),
          onClick --> sideEffect(scribe.info(s"$title($postChoice) = NO")),
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
        button(positiveText,
          onClick(graphChangesYes) --> ObserverSink(state.eventProcessor.enriched.changes),
          onClick(TaskFeedback(true, true, graphChangesYes)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
        ),
        button(negativeText, onClick(TaskFeedback(true, false, graphChangesYes)) --> RestructuringTaskGenerator.taskDisplayWithLogging),
        width := "100%",
      )
    )
  }
}

sealed trait AddTagTask extends RestructuringTask
{

  def constructComponent(sourcePosts: List[Post], targetPosts: List[Post], sink: Sink[String]): VNode = {

    val userInput = Handler.create[String].unsafeRunSync()
    def textAreaWithEnterAndLog(actionSink: Sink[String]) = {
      val clearHandler = userInput.map(_ => "")

      textArea(
        width := "100%",
        value <-- clearHandler,
        managed(actionSink <-- userInput),
        onKeyDown.collect { case e if e.keyCode == KeyCode.Enter && !e.shiftKey => e.preventDefault(); e }.value.filter(_.nonEmpty) --> userInput
      )
    }

    div(
      sourcePosts.map(stylePost)(breakOut): List[VNode],
      targetPosts.map(stylePost)(breakOut): List[VNode],
      div(
        textAreaWithEnterAndLog(sink)(
          Placeholders.newTag,
          flex := "0 0 3em",
        ),
//        button(positiveText,
//          onClick(TaskFeedback(true, true, userInput)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
//        ),
        button(negativeText,
          onClick(TaskFeedback(true, false, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
        ),
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
  val positiveText: String = "Verbinden"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |Is the first post related to the other post(s)?
      |
      |If yes - they will be connected.
    """.stripMargin
  val descriptionGer: String =
    """
      |Steht der erste Beitrag in Beziehung zu den Anderen?
    """.stripMargin

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
  val positiveText: String = "Verbinden"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |How would you tag the relation between the first post and the others?
      |
      |Enter a tag that describes their relation!
      |
      |You can just type any tag and press Enter to confirm the tag.
    """.stripMargin
  val descriptionGer: String =
    """
      |Wie würden Sie die Beziehung des ersten Beitrags mit den Weiteren beschreiben?
      |
      |Geben Sie einen Tag ein, der dessen Beziehung in einem Wort beschreibt!
      |
      |Sie können einfach einen Tag in das Eingabefeld eingeben und mit der Enter-Taste bestätigen.
    """.stripMargin


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

      RestructuringTaskGenerator.taskDisplayWithLogging.unsafeOnNext(TaskFeedback(true, true, changes))

      changes
    }
  }

  def component(state: GlobalState): VNode = {
    val sourcePosts = posts.take(1)
    val targetPosts = posts.slice(1, 2)

    constructComponent(sourcePosts, targetPosts, tagConnection(sourcePosts, targetPosts, state))
  }
}

object SameTopicPosts extends RestructuringTaskObject {
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

  def apply(posts: Posts): SameTopicPosts = new SameTopicPosts(posts)
}
case class SameTopicPosts(posts: Posts) extends YesNoTask
{
  val title = "Topic Posts"
  val positiveText: String = "Zuordnen"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |Does the first post topic description of the others?
      |
      |In other words: Does the later posts follow the first post contentual?
      |
      |If so, the first post represents a new thread within the current discussion and the later posts will be moved
      |into this thread.
    """.stripMargin
  val descriptionGer: String =
    """
      |Beschreibt der erste Beitrag ein eigenes Thema, dem die weiteren Beiträge zugeordnet werden sollen?
      |
      |Beziehungsweise: Folgen die weiteren Beiträge dem ersten Beitrag inhaltlich?
      |
      |Falls ja, so repräsentiert der erste Post eine Unterdiskussion innerhalb der aktuellen Diskussion und die weitern
      |Posts werden in diese Unterdiskussion verschoben.
    """.stripMargin

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
  val positiveText: String = "Zusammenführen"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |Does these posts state the same but in different words?
      |
      |If yes, their content will be merged into a single post.
    """.stripMargin
  val descriptionGer: String =
    """
      |Ist die (inhaltliche) Aussage der beiden Posts gleich?
    """.stripMargin

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
  val positiveText: String = "Vereinen"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |Does these posts state the same and are redundant?
      |
      |If yes, they will be unified.
      |
      |Therefore - only the first post will be kept while the other will be removed.
    """.stripMargin
  val descriptionGer: String =
    """
      |Sind die Posts inhaltlich identisch und somit redundant?
      |
      |Falls ja - so werden die Posts vereint.
      |
      |Dafür wird lediglich der erste Post beibehalten, während die anderen gelöscht werden.
    """.stripMargin

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
  private val defaultNumMaxPosts = Some(1)

  private val possibleHeuristics: List[HeuristicParameters] = List(
    HeuristicParameters(1,  PostHeuristic.Random.heuristic,     defaultMeasureBoundary, defaultNumMaxPosts),
    HeuristicParameters(1,  PostHeuristic.NodeDegree.heuristic, None,                   Some(-1)),
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
  val positiveText: String = "Löschen"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |Is this posts irrelevant for this discussion (e.g. empty phrase / spam)?
      |
      |If yes - the post will be deleted.
    """.stripMargin
  val descriptionGer: String =
    """
      |Ist der angegebene Beitrag irrelevant für die Diskussion (z.B. Floskel / Spam)?
    """.stripMargin

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
  val positiveText: String = "Teilen"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |Does this Post contain multiple / different statements?
      |
      |Please split the post so that each chunk contains a separate statement.
      |
      |You can split a part of this post by selecting it.
      |
      |If you select a statement in the middle of the post - it will be splitted in 3 chunks:
      |The text before the selection, the selected text and the text after the selection.
      |
      |Confirm the selection with the button when you are finished.
    """.stripMargin
  val descriptionGer: String =
    """
      |Beinhaltet der Beitrag unterschiedliche Aussagen?
      |
      |Bitte teilen Sie den Post in Sinneseinheiten ein, sodass jede Einheit eine separate Aussage repräsentiert.
      |
      |Sie können den Beitrag aufteilen, indem Sie einen Teil markieren.
      |
      |Falls Sie eine Aussage in der Mitte des Beitrags makieren, so wird der Beitrag in drei Einheiten geteilt.
      |Eine Einheit vor dem Markierung, eine Einheit für die Markierung und eine nach der Markierung unterteilt.
    """.stripMargin

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

  def generateGraphChanges(originalPosts: List[Post], previewPosts: List[List[Post]], graph: Graph): GraphChanges = {

    if(previewPosts.isEmpty) return GraphChanges.empty

    val keepRelatives = originalPosts.flatMap(originalPost => previewPosts.last.flatMap(p => transferChildrenAndParents(graph, originalPost.id, p.id))).toSet
    val newConnections = originalPosts.flatMap(originalPost => previewPosts.last.map(p => Connection(p.id, Label("splitFrom"), originalPost.id))).toSet
    val newPosts = originalPosts.flatMap(originalPost => previewPosts.last.filter(_.id != originalPost.id)).toSet
    GraphChanges(
      addPosts = newPosts,
      addConnections = newConnections ++ keepRelatives,
      delPosts = originalPosts.map(_.id).toSet,
    )
  }

  def component(state: GlobalState): VNode = {
    val splitPost = posts.take(1)
    val postPreview = Handler.create[List[List[Post]]](List(splitPost)).unsafeRunSync()

    div(
      div(
        children <-- postPreview.map {posts =>
          posts.last.map {post =>
            div(
              mdHtml(post.content),
              color.black,
              maxWidth := "60%",
              backgroundColor := "#eee",
              padding := "5px 10px",
              borderRadius := "7px",
              border := "1px solid gray",
              margin := "5px 0px",
              onMouseUp.map(e => posts :+ posts.last.flatMap(p => if(p == post) splittedPostPreview(e, post, state) else List(p))) --> postPreview,
            )
          }
        },
        width := "100%",
      ),
      button(positiveText,
        onClick(postPreview).map(generateGraphChanges(splitPost, _, getGraphFromState(state))) --> ObserverSink(state.eventProcessor.enriched.changes),
        onClick(postPreview).map(preview => TaskFeedback(true, true, generateGraphChanges(splitPost, preview, getGraphFromState(state)))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
      ),
      button("Rückgängig",
        onClick(postPreview.map(ll => ll.takeRight(2).take(1))) --> postPreview,
      ),
      button("Reset",
        onClick(List(splitPost)) --> postPreview,
      ),
      button(negativeText,
        onClick(postPreview).map(p => TaskFeedback(true, false, generateGraphChanges(splitPost, p, getGraphFromState(state)))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
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
  val positiveText: String = "Kategorisieren"
  val negativeText: String = "Abbrechen"
  val descriptionEng: String =
    """
      |How would you describe this post?
      |Please add a tag!
      |
      |This will categorize the post within this discussion.
      |
      |You can confirm the tag by pressing Enter.
    """.stripMargin
  val descriptionGer: String =
    """
      |Wie würden Sie den Beitrag kategorisieren?
      |Fügen Sie einen Tag hinzu!
      |
      |Damit kategorisieren Sie den Post innerhalb der aktuellen Diskussion.
      |
      |Sie können den Tag bestätigen indem Sie Enter drücken.
    """.stripMargin

  def addTagToPost(post: List[Post], state: GlobalState): Sink[String] = {

    ObserverSink(state.eventProcessor.changes).redirectMap { (tag: String) =>
      val graph = getGraphFromState(state)
      val tagPostWithParents: GraphChanges = graph.posts.find(_.content == tag) match {
        case None =>
          val newTag = Post(PostId.fresh, tag, state.inner.currentUser.now.id)
          val newParent = state.inner.page.now.parentIds
          val postTag = post.map(p => Connection(p.id, Label.parent, newTag.id))
          GraphChanges(
            addPosts = Set(newTag),
            addConnections = newParent.map(parent => Connection(newTag.id, Label.parent, parent)) ++ postTag
          )
        case Some(t) =>
          post.map(p => GraphChanges.connect(p.id, Label.parent, t.id)).reduceLeft((gc1, gc2) => gc2.merge(gc1))
      }

      RestructuringTaskGenerator.taskDisplayWithLogging.unsafeOnNext(TaskFeedback(true, true, tagPostWithParents))

      tagPostWithParents
    }
  }

  def component(state: GlobalState): VNode = {
    val postsToTag = posts
    constructComponent(postsToTag, addTagToPost(postsToTag, state))
  }
}

object RestructuringTaskGenerator {

  // Tasks for study
  val tasks: List[RestructuringTaskObject] = List(
    AddTagToPosts,
    DeletePosts,
    //    ConnectPosts,
    ConnectPostsWithTag,
    SameTopicPosts,
    MergePosts,
    SplitPosts,
    //    UnifyPosts,
  )

  val taskDisplayWithLogging: Handler[TaskFeedback] = Handler.create[TaskFeedback](TaskFeedback(false, false, GraphChanges.empty)).unsafeRunSync()

  def renderButton(activateTasks: Boolean): VNode = {
    val buttonType = if(activateTasks) {
      button("Close tasks!", width := "100%", onClick(TaskFeedback(displayNext = false, false, GraphChanges.empty)) --> taskDisplayWithLogging)
    } else {
      button("Task me!", width := "100%", onClick(TaskFeedback(displayNext = true, false, GraphChanges.empty)) --> taskDisplayWithLogging)
    }
    div(
      span("Tasks"),
      fontWeight.bold,
      fontSize := "20px",
      marginBottom := "10px",
      buttonType
    )
  }

  def render(globalState: GlobalState): Observable[VNode] = taskDisplayWithLogging.map{ feedback =>

    scribe.info(s"display task! ${feedback.toString}")
    Client.api.log(feedback.toString)
    if(feedback.displayNext) {
      div(
        renderButton(activateTasks = true),
        children <-- Observable.fromFuture(composeTask(globalState).map(_.map(_.render(globalState))))
      )
    } else {
      renderButton(activateTasks = false)
    }
  }

  // Currently, we choose a task at random and decide afterwards which heuristic is used
  // But it is also possible to decide a task based on given post metrics
  // Therefore if we can find / define metrics of a discussion, we can choose an
  // applicable task after choosing a post (based on a discussion metric)
  private def composeTask(globalState: GlobalState): Future[List[RestructuringTask]] = {

    val graph = globalState.inner.displayGraphWithoutParents.now.graph
    val task = ChooseTaskHeuristic.random(tasks)
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

  private var _studyTaskIndex: Int = 1
  private var initLoad = false
  private var initGraph = Graph.empty
  private var initState: Option[GlobalState] = None

    private var _studyTaskList = Future.successful(List.empty[RestructuringTask])

  def renderStudy(globalState: GlobalState): Observable[VNode] = taskDisplayWithLogging.map{ feedback =>

    if(feedback.displayNext) {
      if(!initLoad) {
        initLoad = true
        initGraph = globalState.inner.displayGraphWithParents.now.graph
        initState = Some(globalState)

        def mapPid(pids: List[PostId]): PostHeuristicType = {
          val posts: List[Post] = pids.map(pid => initGraph.postsById(pid))
          val h: PostHeuristicType = PostHeuristic.Deterministic(posts).heuristic
          h
        }


        def mapTask(t: RestructuringTaskObject, l: List[PostId]) = t.applyWithStrategy(initGraph, mapPid(l))
        //        val tmpStudyTaskList: List[Future[List[RestructuringTask]]] =
        //          (for(p <- initGraph.postIds) yield mapTask(SplitPosts, List(PostId(p)))).toList

        val tmpStudyTaskList = List(
          mapTask(DeletePosts,          List(PostId("107"))),                 //11
          mapTask(SplitPosts,           List(PostId("108"))),                 //18
          mapTask(SameTopicPosts,         List(PostId("126"), PostId("127"))),  //7
          mapTask(MergePosts,           List(PostId("119"), PostId("132"))),  //13
          mapTask(SameTopicPosts,         List(PostId("132"), PostId("119"))),  //9
          mapTask(SplitPosts,           List(PostId("103"))),                 //16
          mapTask(DeletePosts,          List(PostId("109"))),                 //12
          mapTask(ConnectPostsWithTag,  List(PostId("116"), PostId("101"))),  //6
          mapTask(MergePosts,           List(PostId("111"), PostId("112"))),  //14
          mapTask(AddTagToPosts,        List(PostId("126"))),                 //3
          mapTask(SplitPosts,           List(PostId("101"))),                 //17
          mapTask(SameTopicPosts,         List(PostId("120"), PostId("117"))),  //8
          mapTask(MergePosts,           List(PostId("113"), PostId("122"))),  //15
          mapTask(DeletePosts,          List(PostId("106"))),                 //10
          mapTask(ConnectPostsWithTag,  List(PostId("114"), PostId("113"))),  //4
          mapTask(AddTagToPosts,        List(PostId("121"))),                 //2
          mapTask(AddTagToPosts,        List(PostId("120"))),                 //1
          mapTask(ConnectPostsWithTag,  List(PostId("109"), PostId("108"))),  //5
        )

        _studyTaskList = Future.sequence(tmpStudyTaskList).map(_.flatten)
      }

      _studyTaskList.foreach { tasks =>
        if (_studyTaskIndex > 0) {
          val taskTitle = tasks(_studyTaskIndex - 1).title
          val str = s"RESTRUCTURING TASKS ${_studyTaskIndex} LOG -> ${if(feedback.taskAnswer) "YES" else "NO"}: $taskTitle -> ${feedback.graphChanges}"
            scribe.info(str)
            Client.api.log(str)
        }
      }

      val doRenderThis: Future[VNode] = _studyTaskList.map { list =>
        if (_studyTaskIndex >= list.size) {
          _studyTaskIndex = 0
          scribe.info("All tasks are finished")
          renderButton(activateTasks = false)
        } else {
          val dom = div(
            renderButton(activateTasks = true),
            list(_studyTaskIndex).render(initState.get)
          )
          _studyTaskIndex = _studyTaskIndex + 1
          dom
        }
      }

      div(
        child <-- Observable.fromFuture(doRenderThis)
      )

    } else {
      _studyTaskIndex = _studyTaskIndex - 1
      renderButton(activateTasks = false)
    }
  }
}

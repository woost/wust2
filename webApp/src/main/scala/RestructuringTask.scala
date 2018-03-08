package wust.webApp

import wust.utilWeb.outwatchHelpers._
import org.scalajs.dom
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

import scala.collection.breakOut
import scala.concurrent.Future

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
  val taskImg: VNode

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

  def arrowDiv(text: String = "", color: String = "black", arrowLengthPixel: Int = 120, arrowHeight: Int = 40, arrowLineHeight: Int = 10): VNode = {
    // div("------------------->"),
    div(
      text,
      div(
        div( //line
          marginTop := s"${((arrowHeight - 2) / 2) - (arrowLineHeight / 2)}px",
          width := s"${arrowLengthPixel - 30}px",
          background := color,
          height := s"${arrowLineHeight}px",
          float.left,
        ) ,
        div( //point
          width := "0",
          height := "0",
          borderTop := s"${arrowHeight / 2}px solid transparent",
          borderBottom := s"${arrowHeight / 2}px solid transparent",
          borderLeft := "30px solid black",
          float.right,
        ),
        width := s"${arrowLengthPixel}px",
      ),
      textAlign := "center",
      margin := "0 3px",
    )
  }

  def stylePost(post: Post): VNode = div(
    mdHtml(post.content),
    textAlign.left,
    color.black,
    maxWidth := "80ch",
    backgroundColor := "#eee",
    padding := "5px 10px",
    borderRadius := "7px",
    border := "0",
    boxShadow := "0 2px 6px 0 gray",
    margin := "5px auto",
  )

  def render(state: GlobalState): VNode = {
    div( //modal outer container
      div( //modal inner container
        div( //header
          paddingTop := "8px",
          margin := "0",
          backgroundColor := "#70db70",
        ),
        div( //content
          div( //Headline
            div(
              "Kalt duschen",
              fontWeight.bold,
              float.left,
            ),
            div(
              "×",
              onClick(TaskFeedback(false, false, GraphChanges.empty)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
              cursor.pointer,
              fontSize := "28px",
              fontWeight.bold,
              float.right,
            ),
            width := "100%",
            padding := "20px 4px",
            clear.both,
          ),
          div( //Task description
            div(
              taskImg,
              flex := "0",
              margin := "auto",
            ),
            div(
              mdHtml(description),
              paddingLeft := "20px",
              flex := "1",
            ),
            display := "flex",
            flexDirection := "row",
            flexWrap := "nowrap",
            alignItems := "center",
            justifyContent := "center",
            padding := "20px 4px",
            margin := "auto",
            clear.both,
          ),
          div( //Task component
            component(state),
            borderTop := "2px dotted",
            padding := "20px 4px",
            width := "100%",
          ),
          padding := "20px 16px",
          color := "black",
        ),
        div(//footer
          padding := "0",
          margin := "0",
          backgroundColor := "green",
        ),
        width := "120ch",
        position.fixed,
        left := "0",
        right := "0",
        bottom := "20px",
        margin := "0 auto",
        borderRadius := "7px",
        border := "0",
        boxShadow := "0 4px 8px 0 rgba(0, 0, 0, 0.2), 0 6px 20px 0 rgba(0, 0, 0, 0.2)",
        backgroundColor := "white",
      ),
      display.block,
      position.fixed,
      zIndex := 100,
      left := "0",
      bottom := "0",
      width := "100%",
      height := "100%",
      overflow.auto,
      // backgroundColor := "rgb(0,0,0)",
      // backgroundColor := "rgba(0,0,0,0.4)",
    )
  }
}

sealed trait YesNoTask extends RestructuringTask
{
  def constructGraphChanges(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): GraphChanges

  def createPreviewNode(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      sourcePosts.map(stylePost)(breakOut): List[VNode],
      targetPosts.map(stylePost)(breakOut): List[VNode],
    )
  }

  def confirmButton(state: GlobalState, graphChanges: GraphChanges): VNode = {
    button(positiveText,
      onClick(graphChanges) --> ObserverSink(state.eventProcessor.enriched.changes),
      onClick(TaskFeedback(true, true, graphChanges)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
    )
  }

  def abortButton(graphChanges: GraphChanges): VNode = {
    button(
      negativeText,
      onClick(TaskFeedback(true, false, graphChanges)) --> RestructuringTaskGenerator.taskDisplayWithLogging,
      marginLeft := "10px",
    )
  }

  def constructComponent(state: GlobalState,
                         sourcePosts: Posts,
                         targetPosts: Posts): VNode = {

    val graphChanges = constructGraphChanges(state, sourcePosts, targetPosts)

    div(
      createPreviewNode(state, sourcePosts, targetPosts),
      div(
        confirmButton(state, graphChanges),
        abortButton(graphChanges),
        width := "100%",
        padding := "20px 4px",
      ),
      width := "100%",
      margin := "auto",
      textAlign := "center",
    )
  }

  def constructComponent(state: GlobalState, targetPosts: Posts): VNode =
    constructComponent(state, List.empty[Post], targetPosts)

}

sealed trait AddTagTask extends RestructuringTask
{

  def constructGraphChanges(state: GlobalState, tag: String, sourcePosts: Posts, targetPosts: Posts): GraphChanges

  def createPreviewNode(state: GlobalState, tagField: VNode, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      sourcePosts.map(stylePost)(breakOut): List[VNode],
      targetPosts.map(stylePost)(breakOut): List[VNode],
    )
  }

  private val userTagInput: Handler[String] = Handler.create[String].unsafeRunSync()

  private def tagConnection(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): Sink[String] = {
    ObserverSink(state.eventProcessor.changes).redirectMap { (tag: String) =>

      val graphChanges = constructGraphChanges(state, tag, sourcePosts, targetPosts)

      RestructuringTaskGenerator.taskDisplayWithLogging.unsafeOnNext(TaskFeedback(true, true, graphChanges))

      graphChanges
    }
  }

  private def tagArea(state: GlobalState,
                      sourcePosts: Posts,
                      targetPosts: Posts): VNode = {

    val sink: Sink[String] = tagConnection(state, sourcePosts, targetPosts)

    textAreaWithEnter(sink)(
      placeholder := "Bitte Tag eingeben",
      flex := "0 0 2em",
      width := "20ch",
      rows := 1,
      style("resize") := "none",
      onChange.value --> userTagInput
    )
  }

  def confirmButton(state: GlobalState, graphChanges: Observable[GraphChanges]): VNode = {
    button(positiveText,
      onClick(graphChanges) --> ObserverSink(state.eventProcessor.enriched.changes),
      onClick(graphChanges.map(TaskFeedback(true, true, _))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
    )
  }

  def abortButton(graphChanges: Observable[GraphChanges]): VNode = {
    button(
      negativeText,
      onClick(graphChanges.map(TaskFeedback(true, false, _))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
      marginLeft := "10px",
    )
  }

  def constructComponent(state: GlobalState,
                         sourcePosts: Posts,
                         targetPosts: Posts): VNode = {
    val graphChanges = userTagInput
      .map(tag => constructGraphChanges(state, tag, sourcePosts, targetPosts))
      .startWith(Seq(GraphChanges.empty))

    div(
      createPreviewNode(state, tagArea(state, sourcePosts, targetPosts), sourcePosts, targetPosts),
      div(
        confirmButton(state, graphChanges),
        abortButton(graphChanges),
        width := "100%",
        padding := "20px 4px",
      ),
      width := "100%",
      margin := "auto",
      textAlign := "center",
    )
  }

  def constructComponent(state: GlobalState, targetPosts: Posts): VNode = {
    constructComponent(state, List.empty[Post], targetPosts)
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
  val negativeText: String = "Nicht verbinden"
  val taskImg: VNode = img(src := "connect.png")
  val descriptionEng: String =
    """
      |Is the first post related to the other post(s)?
      |
      |If yes - they will be connected.
    """.stripMargin
  val descriptionGer: String =
    """
      |Steht der erste Beitrag in Beziehung zu dem Anderen?
    """.stripMargin

  def constructGraphChanges(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): GraphChanges = {
    val source = sourcePosts.head
    val connectionsToAdd = for {
      t <- targetPosts if t.id != source.id
    } yield {
        Connection(source.id, Label("related"), t.id)
      }
    GraphChanges(addConnections = connectionsToAdd.toSet)
  }

  override def createPreviewNode(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      div(
        sourcePosts.map(stylePost)(breakOut): List[VNode],
        flex := "1",
      ),
      arrowDiv("related"),
      div(
        targetPosts.map(stylePost)(breakOut): List[VNode],
        flex := "1",
      ),
      display := "flex",
      alignItems := "center",
      flexDirection := "row",
      flexWrap := "nowrap",
      justifyContent := "center"
    )
  }

  def component(state: GlobalState): VNode = {
    val (sourcePosts, targetPosts) = posts.splitAt(1)

    constructComponent(state, sourcePosts, targetPosts)
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
  val negativeText: String = "Nicht verbinden"
  val taskImg: VNode = img(src := "connectWithTag.png")
  val descriptionEng: String =
    """
      |How would you tag the relation between the first post and the others?
      |
      |Enter a tag that describes their relation.
      |
      |You can just type any tag and press Enter to confirm the tag.
    """.stripMargin
  val descriptionGer: String =
    """
      |Wie würden Sie die Beziehung des ersten Beitrags mit dem Zweiten beschreiben?
      |
      |Geben Sie einen Tag (Label / Relation) ein, der dessen Beziehung in ein bis zwei Worten beschreibt.
      |
      |Sie können einfach einen Tag in das Eingabefeld eingeben und mit der Enter-Taste bestätigen.
    """.stripMargin

  def constructGraphChanges(state: GlobalState,
                            tag: String,
                            sourcePosts: Posts,
                            targetPosts: Posts): GraphChanges = {

    scribe.info(s"construct graph changes: $tag, $sourcePosts, $targetPosts")
    if(tag.isEmpty) return GraphChanges.empty

    val tagConnections = for {
      s <- sourcePosts
      t <- targetPosts if s.id != t.id
    } yield {
      Connection(s.id, Label(tag), t.id)
    }

    GraphChanges(
      addConnections = tagConnections.toSet
    )
  }

  override def createPreviewNode(state: GlobalState, tagField: VNode, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      div(
        sourcePosts.map(stylePost)(breakOut): List[VNode],
        margin := "auto",
      ),
      div(
        div(
          tagField,
          paddingBottom := "3px",
        ),
        arrowDiv(arrowLengthPixel = 250, arrowHeight = 30, arrowLineHeight = 6),
        textAlign := "center",
        margin := "auto",
      ),
      div(
        targetPosts.map(stylePost)(breakOut): List[VNode],
        margin := "auto",
      ),
      display := "flex",
      flexDirection := "row",
      flexWrap := "nowrap",
      justifyContent := "center"
    )
  }

  def component(state: GlobalState): VNode = {
    val (sourcePosts, targetPosts) = posts.splitAt(1)
    constructComponent(state, sourcePosts, targetPosts)
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
  val negativeText: String = "Nicht zuordnen"
  val taskImg: VNode = img(src := "sameTopic.png")
  val descriptionEng: String =
    """
      |Does the first post topic description of the others?
      |
      |In other words: Does the later posts follow the first post contentual?
      |
      |If so, the first post represents a new thread within the current discussion and the later posts will be moved
      |into this thread.
    """.stripMargin
  //      |Beschreibt der erste Beitrag ein eigenes Thema, dem der zweite Beitrag zugeordnet werden sollen?
  val descriptionGer: String =
    """
      |Startet der erste Beitrag eine neue Unterdiskussion, zu welcher der zweite Beitrag dazugehört?
      |
      |Falls ja, so repräsentiert der erste Beitrag eine Unterdiskussion innerhalb der aktuellen Diskussion und
      |der zweite Beitrag wird in diese Unterdiskussion verschoben.
    """.stripMargin

  def constructGraphChanges(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): GraphChanges = {
    val target = targetPosts.head
    val containmentsToAdd = for {
      s <- sourcePosts if s.id != target.id
    } yield {
        Connection(s.id, Label.parent, target.id)
    }

    GraphChanges(addConnections = containmentsToAdd.toSet)
  }

  override def createPreviewNode(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      div(
        targetPosts.map(stylePost)(breakOut): List[VNode],
        padding := "2px 3px",
        flex := "1",
      ),
      div(
        sourcePosts.map(stylePost)(breakOut): List[VNode],
        padding := "2px 3px",
        flex := "1",
      ),
      div(
        arrowDiv(),
        textAlign := "center",
        margin := "auto",
      ),
      div(
        div(
          mdHtml(targetPosts.head.content),
          textAlign.left,
          color.black,
          maxWidth := "80ch",
          backgroundColor := "#eee",
          padding := "5px 10px",
          borderRadius := "7px",
          border := "0",
          boxShadow := "0 2px 6px 0 gray",
          margin := "5px auto",
          div(
            mdHtml(sourcePosts.head.content),
            textAlign.left,
            color.black,
            maxWidth := "80ch",
            backgroundColor := "#eee",
            padding := "5px 10px",
            borderRadius := "7px",
            border := "0",
            boxShadow := "0 2px 6px 0 gray",
            margin := "5px auto",
          ),
        ),
        padding := "2px 3px",
        flex := "1",
      ),
      display := "flex",
      flexDirection := "row",
      flexWrap := "nowrap",
      alignItems := "center",
      justifyContent := "center",
    )
  }

  def component(state: GlobalState): VNode = {
    val (targetPosts, sourcePosts) = posts.splitAt(1)

    constructComponent(state, sourcePosts, targetPosts)
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
  val negativeText: String = "Nicht zusammeführen"
  val taskImg: VNode = img(src := "merge.png")
  val descriptionEng: String =
    """
      |Does these posts state the same but in different words?
      |
      |If yes, their content will be merged into a single post.
    """.stripMargin
  val descriptionGer: String =
    """
      |Ist die (inhaltliche) Aussage der beiden Beiträge gleich?
    """.stripMargin

  def mergePosts(mergeTarget: Post, source: Post): Post = {
    mergeTarget.copy(content = mergeTarget.content + "\n\n" + source.content)
  }

  def constructGraphChanges(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): GraphChanges = {
    val graph = getGraphFromState(state)
    val target = targetPosts.head
    val postsToUpdate = for {
      source <- sourcePosts if source.id != target.id
    } yield {
      (mergePosts(target, source), transferChildrenAndParents(graph, source.id, target.id))
    }

    GraphChanges(
      addConnections = postsToUpdate.flatMap(_._2).toSet,
      updatePosts = postsToUpdate.map(_._1).toSet,
      delPosts = sourcePosts.map(_.id).toSet)
  }

  override def createPreviewNode(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      div(
        targetPosts.map(stylePost)(breakOut): List[VNode],
        padding := "2px 3px",
        flex := "1",
      ),
      div(
        sourcePosts.map(stylePost)(breakOut): List[VNode],
        padding := "2px 3px",
        flex := "1",
      ),
      div(
        arrowDiv(),
        textAlign := "center",
        margin := "auto",
      ),
      div(
        stylePost(sourcePosts.foldLeft(targetPosts.head)(mergePosts)),
        padding := "2px 3px",
        flex := "1",
      ),
      display := "flex",
      flexDirection := "row",
      flexWrap := "nowrap",
      alignItems := "center",
      justifyContent := "center",
      width := "100%",
    )
  }

  def component(state: GlobalState): VNode = {
    val (targetPosts, sourcePosts) = posts.splitAt(1)
    constructComponent(state, sourcePosts, targetPosts)
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
  val negativeText: String = "Nicht vereinen"
  val taskImg: VNode = img(src := "merge.png")
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
      |Sind die Beiträge inhaltlich identisch und somit redundant?
      |
      |Falls ja - so werden die Beiträge vereint.
      |
      |Dafür wird lediglich der erste Beitrag beibehalten, während die anderen gelöscht werden.
    """.stripMargin

  def unifyPosts(unifyTarget: Post, post: Post): Post = {
    unifyTarget.copy(content = unifyTarget.content + "\n\n" + post.content)
  }

  def constructGraphChanges(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): GraphChanges = {
    val graph = getGraphFromState(state)
    val target = targetPosts.head
    val postsToUpdate = for {
      source <- sourcePosts if source.id != target.id
    } yield {
      (unifyPosts(target, source), transferChildrenAndParents(graph, source.id, target.id))
    }

    GraphChanges(
      addConnections = postsToUpdate.flatMap(_._2).toSet,
      updatePosts = postsToUpdate.map(_._1).toSet,
      delPosts = sourcePosts.map(_.id).toSet)
  }

  override def createPreviewNode(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): VNode = {
    div(
      div(
        targetPosts.map(stylePost)(breakOut): List[VNode],
        padding := "2px 3px",
        flex := "1",
      ),
      div(
        sourcePosts.map(stylePost)(breakOut): List[VNode],
        padding := "2px 3px",
        flex := "1",
      ),
      div(
        div(
          confirmButton(state, constructGraphChanges(state, sourcePosts, targetPosts)),
          paddingBottom := "3px",
        ),
        arrowDiv(),
        textAlign := "center",
        margin := "auto",
      ),
      div(
        stylePost(sourcePosts.foldLeft(targetPosts.head)(unifyPosts)),
        padding := "2px 3px",
        flex := "1",
      ),
      display := "flex",
      flexDirection := "row",
      flexWrap := "nowrap",
      alignItems := "center",
      justifyContent := "center",
      width := "100%",
    )
  }

  def component(state: GlobalState): VNode = {
    val (targetPosts, sourcePosts) = posts.splitAt(1)
    constructComponent(state, sourcePosts, targetPosts)
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
  val negativeText: String = "Nicht löschen"
  val taskImg: VNode = img(src := "delete.png")
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

  def constructGraphChanges(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): GraphChanges = {
    GraphChanges(delPosts = targetPosts.map(_.id).toSet)
  }

//  override def createPreviewNode(state: GlobalState, sourcePosts: Posts, targetPosts: Posts): VNode = div("TODO")

  def component(state: GlobalState): VNode = {
    constructComponent(state, posts)
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
  val positiveText: String = "Aufteilung abschicken"
  val negativeText: String = "Aufteilung abbrechen"
  val taskImg: VNode = img(src := "split2.png")
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
      |Bitte teilen Sie den Beitrag in Sinneseinheiten ein, sodass jede Einheit eine separate Aussage repräsentiert.
      |
      |Sie können den Beitrag aufteilen, indem Sie einen Teil markieren.
      |
      |Alternativ können Sie an eine entsprechende Stelle klicken, um den Beitrag an dieser Stelle zu teilen.
    """.stripMargin

  def stringToPost(str: String, condition: Boolean, state: GlobalState): Option[Post] = {
    if(!condition || str.trim.isEmpty) return None
    Some(Post(PostId.fresh, str.trim, state.inner.currentUser.now.id))
  }

  def splittedPostPreview(event: MouseEvent, originalPost: Post, state: GlobalState): Posts = {
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

  def generateGraphChanges(originalPosts: Posts, previewPosts: List[Posts], graph: Graph): GraphChanges = {

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
    val postPreview = Handler.create[List[Posts]](List(splitPost)).unsafeRunSync()

    div(
      div(
        children <-- postPreview.map {posts =>
          posts.last.map {post =>
            div(
              (post.content),
              color.black,
              textAlign.left,
              maxWidth := "60%",
              backgroundColor := "#eee",
              padding := "5px 10px",
              borderRadius := "7px",
              border := "0",
              boxShadow := "0 2px 6px 0 gray",
              margin := "5px auto",
              onMouseUp.map(e => posts :+ posts.last.flatMap(p => if(p == post) splittedPostPreview(e, post, state) else List(p))) --> postPreview,
            )
          }
        },
        textAlign.center,
        width := "100%",
        margin := "auto",
      ),
      div(
        button(
          "Rückgängig",
          onClick(postPreview.map(ll => ll.takeRight(2).take(1))) --> postPreview,
        ),
        button(
          "Zurücksetzen",
          onClick(List(splitPost)) --> postPreview,
          marginLeft := "10px",
        ),
        button(
          positiveText,
          onClick(postPreview).map(generateGraphChanges(splitPost, _, getGraphFromState(state))) --> ObserverSink(state.eventProcessor.enriched.changes),
          onClick(postPreview).map(preview => TaskFeedback(true, true, generateGraphChanges(splitPost, preview, getGraphFromState(state)))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
          marginLeft := "50px",
        ),
        button(
          negativeText,
          onClick(postPreview).map(p => TaskFeedback(true, false, generateGraphChanges(splitPost, p, getGraphFromState(state)))) --> RestructuringTaskGenerator.taskDisplayWithLogging,
          marginLeft := "10px",
        ),
        padding := "3px 20px",
      ),
      textAlign := "center",
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
  val negativeText: String = "Nicht kategorisieren"
  val taskImg: VNode = img(src := "tag.png")
  val descriptionEng: String =
    """
      |How would you describe this post?
      |Please add a tag.
      |
      |This will categorize the post within this discussion.
      |
      |You can confirm the tag by pressing Enter.
    """.stripMargin
  val descriptionGer: String =
    """
      |Wie würden Sie den Beitrag kategorisieren?
      |Fügen Sie einen Tag (Label / Kategorie) hinzu.
      |
      |Damit kategorisieren Sie den Beintrag innerhalb der aktuellen Diskussion.
      |
      |Sie können den Tag bestätigen indem Sie Enter drücken.
    """.stripMargin

  override def constructGraphChanges(state: GlobalState,
                                     tag: String,
                                     sourcePosts: Posts,
                                     targetPosts: Posts): GraphChanges = {

    val graph = getGraphFromState(state)

    val tagPostWithParents: GraphChanges = graph.posts.find(_.content == tag) match {
      case None =>
        val newTag = Post(PostId.fresh, tag, state.inner.currentUser.now.id)
        val newParent = state.inner.page.now.parentIds
        val postTag = targetPosts.map(p => Connection(p.id, Label.parent, newTag.id))
        GraphChanges(
          addPosts = Set(newTag),
          addConnections = newParent.map(parent => Connection(newTag.id, Label.parent, parent)) ++ postTag
        )
      case Some(t) =>
        targetPosts.map(p => GraphChanges.connect(p.id, Label.parent, t.id)).reduceLeft((gc1, gc2) => gc2.merge(gc1))
    }

    tagPostWithParents
  }

  override def createPreviewNode(state: GlobalState,
                                 tagField: VNode,
                                 sourcePosts: Posts,
                                 targetPosts: Posts): VNode = {

    val postComponents = targetPosts.map { p =>
      div(
        div(
          mdHtml(p.content),
          color.black,
          textAlign.left,
          maxWidth := "80ch",
        ),
        tagField(
          fontSize.small,
          float.right,
          width := "20ch",
          color := "#2db300",
          padding := "0px 3px",
        ),
        div(clear.right),
        backgroundColor := "#eee",
        padding := "5px 10px",
        borderRadius := "7px",
        border := "0",
        boxShadow := "0 2px 6px 0 gray",
        margin := "5px auto",
        maxWidth := "80ch",
      )
    }(breakOut): List[VNode]

    div(
      postComponents,
      width := "100%",
      margin := "5px 0px",
    )
  }

  def component(state: GlobalState): VNode = {
    constructComponent(state, posts)
  }
}

object RestructuringTaskGenerator {

  // Tasks for study
  val tasks: List[RestructuringTaskObject] = List(
    AddTagToPosts,
    ConnectPosts,
    ConnectPostsWithTag,
    DeletePosts,
    MergePosts,
    SameTopicPosts,
    SplitPosts,
    UnifyPosts,
  )

  //TrimTask -> delete empty phrases in post

  val taskDisplayWithLogging: Handler[TaskFeedback] = Handler.create[TaskFeedback](TaskFeedback(false, false, GraphChanges.empty)).unsafeRunSync()

  def renderButton(activateTasks: Boolean): VNode = {
    val buttonType = if(activateTasks) {
      button("Task Studie beenden / unterbrechen", width := "100%", onClick(TaskFeedback(displayNext = false, false, GraphChanges.empty)) --> taskDisplayWithLogging)
    } else {
      button("Task Studie beginnen / fortsetzen", width := "100%", onClick(TaskFeedback(displayNext = true, false, GraphChanges.empty)) --> taskDisplayWithLogging)
    }
    div(
      fontWeight.bold,
      fontSize := "20px",
      buttonType
    )
  }

  def render(globalState: GlobalState): Observable[VNode] = taskDisplayWithLogging.map{ feedback =>

    scribe.info(s"display task ${feedback.toString}")
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
            val posts: Posts = pids.map(pid => initGraph.postsById(pid))
            val h: PostHeuristicType = PostHeuristic.Deterministic(posts).heuristic
            h
          }

          def mapTask(t: RestructuringTaskObject, l: List[PostId]) = t.applyWithStrategy(initGraph, mapPid(l))

          // val graph = globalState.inner.displayGraphWithoutParents.now.graph
          // val tmpStudyTaskList: List[Future[List[RestructuringTask]]] = List(
          //   AddTagToPosts.applyStrategically(graph),
          //   ConnectPostsWithTag.applyStrategically(graph),
          //   DeletePosts.applyStrategically(graph),
          //   MergePosts.applyStrategically(graph),
          //   SameTopicPosts.applyStrategically(graph),
          //   SplitPosts.applyStrategically(graph),
          //   // ConnectPosts.applyStrategically(graph),
          //   // UnifyPosts.applyStrategically(graph),
          //   )
          // DEBUG purpose
          // val tmpStudyTaskList: List[Future[List[RestructuringTask]]] = List(
          //   mapTask(AddTagToPosts, List(PostId("111"))),
          //   mapTask(ConnectPostsWithTag, List(PostId("111"), PostId("112"))),
          //   mapTask(DeletePosts, List(PostId("111"))),
          //   mapTask(MergePosts, List(PostId("111"), PostId("112"))),
          //   mapTask(SameTopicPosts, List(PostId("111"), PostId("112"))),
          //   mapTask(SplitPosts, List(PostId("111"))),
          // mapTask(ConnectPosts, List(PostId("111"), PostId("112"))),
          // mapTask(UnifyPosts, List(PostId("111"), PostId("112"))),
          // )
          // val tmpStudyTaskList: List[Future[List[RestructuringTask]]] =
          //           (for(p <- initGraph.postIds) yield mapTask(ConnectPostsWithTag, List(PostId(p), PostId(p)))).toList

          val tmpStudyTaskList = List(
            mapTask(DeletePosts,          List(PostId("107"))),                 //11
            mapTask(SplitPosts,           List(PostId("108"))),                 //18
            mapTask(SameTopicPosts,       List(PostId("126"), PostId("127"))),  //7
            mapTask(MergePosts,           List(PostId("119"), PostId("132"))),  //13
            mapTask(SameTopicPosts,       List(PostId("132"), PostId("119"))),  //9
            mapTask(SplitPosts,           List(PostId("103"))),                 //16
            mapTask(DeletePosts,          List(PostId("109"))),                 //12
            mapTask(ConnectPostsWithTag,  List(PostId("116"), PostId("101"))),  //6
            mapTask(MergePosts,           List(PostId("111"), PostId("112"))),  //14
            mapTask(AddTagToPosts,        List(PostId("126"))),                 //3
            mapTask(SplitPosts,           List(PostId("101"))),                 //17
            mapTask(SameTopicPosts,       List(PostId("120"), PostId("117"))),  //8
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

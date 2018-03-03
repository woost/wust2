package space.woost

import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.support.v7.widget.LinearLayoutManager
import macroid.Contexts
import android.widget.{FrameLayout, LinearLayout, ProgressBar}
import android.view.View
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener
import android.support.v4.app.Fragment
import android.app.Activity
import android.os.Bundle
import android.widget.{Button, LinearLayout, TextView, ScrollView, EditText}
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import macroid._
import macroid.contrib._
import macroid.contrib.Layouts._
import macroid.extras.FragmentExtras._
import macroid.extras.ToolbarTweaks._
import macroid.FullDsl._
import covenant.ws._
import wust.api._
import mycelium.client.OkHttpWebsocketConnection
import java.nio.ByteBuffer
import macroid._
import wust.sdk._
import concurrent.Future
import wust.api.serialize.Boopickle._
import boopickle.Default._
import chameleon.ext.boopickle._
import macroid.extras.LinearLayoutTweaks._
import macroid.extras.RecyclerViewTweaks.{W, _}
import macroid.extras.ViewTweaks._
import android.view.LayoutInflater

import macroid.IdGenerator
import wust.graph._
import wust.ids._
import cool.graph.cuid.Cuid
import concurrent.duration._
import mycelium.client._
import monix.execution.Scheduler.Implicits.global
import wust.sdk.EventProcessor
import monix.reactive.{Observable, Observer}

object Id extends IdGenerator(start = 1000)

class MainActivity extends Activity with Contexts[Activity] {
  def cuid = scala.util.Random.alphanumeric.take(36).mkString

  val con = new OkHttpWebsocketConnection[ByteBuffer]
  val wustClient = new WustClientFactory(WsClient.fromConnection[ByteBuffer, ApiEvent, ApiError]("wss://core.staging.woost.space/ws", con, WustClient.config, new sloth.LogHandler[Future]))
  val client = wustClient.sendWith(SendType.WhenConnected, 30 seconds)

  val assumedLogin = UserId(cuid)
  wustClient.observable.connected.foreach(_ => client.auth.assumeLogin(assumedLogin)); //TODO: loginflow

  val eventProcessor = EventProcessor(
    rawEventStream = wustClient.observable.event,
    syncDisabled = Observable(false),
    enrich = (changes, graph) => changes,
    sendChange = client.api.changeGraph _
  )
  val rawGraph:Observable[Graph] = eventProcessor.rawGraph
  var rawGraphNow = Graph.empty // TODO: replace with rx


  var chatHistorySlot = slot[RecyclerView]
  def chatHistory(implicit ctx: ContextWrapper) = {
    w[RecyclerView] <~ rvLayoutManager(new LinearLayoutManager(ctx.application)) <~ wire(chatHistorySlot)
  }
  def updateChatHistory(posts:IndexedSeq[Post]) = {
    // TODO: https://android.jlelse.eu/smart-way-to-update-recyclerview-using-diffutil-345941a160e0
    Ui {
      println("start updating history")
    } ~ (chatHistorySlot <~ rvAdapter(new PostsAdapter(posts)) <~ vInvalidate) ~ Ui {
      println("done updating history")
    }
  }

  rawGraph.foreach { graph => 
    updateChatHistory(graph.chronologicalPostsAscending).run
    rawGraphNow = graph
  }

  def chatInput = {
    var value = slot[EditText]
    l[HorizontalLinearLayout](
      w[EditText] <~ wire(value) <~ llMatchWeightHorizontal,
      w[Button] <~ text("Send") <~
      On.click {
        val content = value.get.getText.toString.trim
        Ui{ 
          if(content.nonEmpty) {
            val post = Post(PostId(cuid), value.get.getText.toString, assumedLogin)
            value.get.getText.clear()
            eventProcessor.applyChanges(GraphChanges.addPost(post)).foreach { _ =>
              println("scrolling down")
              (chatHistorySlot <~ Tweak[RecyclerView](_.smoothScrollToPosition(rawGraphNow.posts.size - 1))).run
            }
          }
        }
      }
    )
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    setContentView {
      Ui.get {
        l[VerticalLinearLayout](
          chatHistory <~ llMatchWeightVertical,
          chatInput
        )
      }
    }
  }
}

class PostsAdapter(posts: IndexedSeq[Post])
    (implicit context: ActivityContextWrapper)
    extends RecyclerView.Adapter[ViewHolderPostsAdapter] {

  override def onCreateViewHolder(parentViewGroup: ViewGroup, i: Int): ViewHolderPostsAdapter = {
    val adapter = new PostsLayoutAdapter()
    new ViewHolderPostsAdapter(adapter)
  }

  override def getItemCount: Int = posts.size

  override def onBindViewHolder(viewHolder: ViewHolderPostsAdapter, position: Int): Unit = {
    val post = posts(position)
    viewHolder.view.setTag(position)
    Ui.run(
      (viewHolder.content <~ text(post.content))
    )
  }
}

class PostsLayoutAdapter(implicit context: ActivityContextWrapper) {

  var content: Option[TextView] = slot[TextView]
  val view: LinearLayout = layout

  private def layout(implicit context: ActivityContextWrapper) = Ui.get(
    l[LinearLayout](
      w[TextView] <~ wire(content)
    )
  )
}

class ViewHolderPostsAdapter(adapter: PostsLayoutAdapter)(implicit context: ActivityContextWrapper)
    extends RecyclerView.ViewHolder(adapter.view) {

  val view: LinearLayout = adapter.view
  val content: Option[TextView] = adapter.content
}

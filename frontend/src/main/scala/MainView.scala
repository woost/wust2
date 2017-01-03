package object frontend {

  import scalajs.concurrent.JSExecutionContext.Implicits.queue
  import org.scalajs.dom._

  import diode._
  import diode.react._
  import japgolly.scalajs.react._
  import japgolly.scalajs.react.vdom.prefix_<^._

  import autowire._
  import boopickle.Default._

  import graph._

  val MainView = ReactComponentB[ModelProxy[RootModel]]("MainView")
    .render_P { proxy =>
      val graph = proxy.value.graph
      <.div(
        // RespondModal(graph),
        <.button("add post", ^.onClick --> Callback {
          Client.wireApi.addPost("Posti").call()
        }),
        <.button("connect something", ^.onClick --> Callback {
          import scala.util.Random.nextInt
          val posts: IndexedSeq[Post] = graph.posts.values.toIndexedSeq
          val n = posts.size
          val source = posts(nextInt(n))
          val target = (posts diff List(source))(nextInt(n - 1))
          Client.wireApi.connect(source.id, target.id).call()
        }),
        <.button(^.onClick --> Callback { Client.logout() }, "logout"),
        GraphView(graph)
      )
    }
    .build
}

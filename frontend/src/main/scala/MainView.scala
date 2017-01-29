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
  import graphview.GraphView

  val GraphConnect = AppCircuit.connect(_.graph)

  val MainView = ReactComponentB[ModelProxy[RootModel]]("MainView")
    .render_P { proxy =>
      val model = proxy.value
      val graph = model.graph
      <.div(
        <.button("add post", ^.onClick --> Callback {
          Client.api.addPost("Posti").call()
        }),
        <.button("connect something", ^.onClick --> Callback {
          import scala.util.Random.nextInt
          val posts: IndexedSeq[Post] = graph.posts.values.toIndexedSeq
          val n = posts.size
          val source = posts(nextInt(n))
          val target = (posts diff List(source))(nextInt(n - 1))
          Client.api.connect(source.id, target.id).call()
        }),
        <.button(^.onClick --> Callback { Client.auth.register("hans", "***").call() }, "register"),
        <.button(^.onClick --> Callback { Client.login(api.PasswordAuth("hans", "***")) }, "login"),
        <.button(^.onClick --> Callback { Client.logout() }, "logout"),
        <.button("graph", ^.onClick --> proxy.dispatchCB(SwitchTab(Tab.Graph))),
        <.button("list", ^.onClick --> proxy.dispatchCB(SwitchTab(Tab.List))),
        model.activeTab match {
          case Tab.Graph => GraphConnect(g => GraphView(g.value))
          case Tab.List => GraphConnect(ListView.component(_))
        },
        model.respondingTo.collect {
          case targetId if graph.posts.isDefinedAt(targetId) =>
            <.div(
              ^.position := "fixed",
              ^.width := "100%",
              ^.bottom := "0",
              ^.background := "#FFF",
              ^.borderTop := "1px solid #DDD",
              RespondForm(graph, targetId)
            )
        }
      )
    }
    .build
}

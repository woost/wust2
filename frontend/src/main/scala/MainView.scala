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
        ^.fontFamily := "sans-serif",

        <.button("register", ^.onClick --> Callback { Client.auth.register("hans", "***").call() }),
        <.button("login", ^.onClick --> Callback { Client.login(api.PasswordAuth("hans", "***")) }),
        <.button("logout", ^.onClick --> Callback { Client.logout() }),
        <.button("graph", ^.onClick --> proxy.dispatchCB(SwitchTab(Tab.Graph))),
        <.button("list", ^.onClick --> proxy.dispatchCB(SwitchTab(Tab.List))),
        model.activeTab match {
          case Tab.Graph => GraphConnect(g => GraphView(g.value))
          case Tab.List => GraphConnect(ListView.component(_))
        },
        <.div(
          ^.position := "fixed",
          ^.width := "100%",
          ^.bottom := "0",
          ^.left := "0",
          ^.padding := "5px",
          ^.background := "#FFF",
          ^.borderTop := "1px solid #DDD",
          AddPostForm(graph, model.focusedPost)
        )
      )
    }
    .build
}

package frontend.views

import org.scalajs.dom._
import mhtml._

import graph._
import frontend.GlobalState

object ListView {
  def component(state: GlobalState) = {
    import state._

    //TODO: performance: https://github.com/OlivierBlanvillain/monadic-html/issues/13
    <div>
      {
        for {
          mode <- mode
          posts <- graph.map(_.posts)
        } yield {
          posts.map(p => <p>{p.toString}</p>)
        }
      }
    </div>
  }
}

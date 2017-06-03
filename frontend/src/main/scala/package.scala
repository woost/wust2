package wust

import wust.graph.Post
import wust.ids.PostId

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("cuid", JSImport.Default)
object Cuid extends js.Object {
  def apply(): String = js.native
}

package object frontend {
  implicit class RichPostFactory(val postFactory: Post.type) extends AnyVal {
    def newId(title: String) = {
      val id = Cuid()
      println("EIDI" + id)
      postFactory.apply(PostId(id), title)
    }
  }
}

package wust

import wust.graph.Post
import wust.ids.{PostId, UserId}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("cuid", JSImport.Default)
object Cuid extends js.Object {
  def apply(): String = js.native
}

package object frontend {
  implicit class RichPostFactory(val postFactory: Post.type) extends AnyVal {
    def newId(content: String, author: UserId) = {
      val id = Cuid()
      postFactory.apply(PostId(id), content, author)
    }
    def newId(content: String, state: GlobalState) = {
      val id = Cuid()
      val author = state.inner.currentUser.now.fold(UserId(-1))(_.id)// TODO: Validation
      postFactory.apply(PostId(id), content, author)
    }
  }
}

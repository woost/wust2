
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, html}
import org.scalajs.dom.raw.NodeList

import scala.scalajs.js
import scala.scalajs.js.`|`
import scala.scalajs.js.annotation._

package hotjar {
  @js.native
  @JSGlobalScope
  object GlobalScope extends js.Object {
    def hj(what:String, page:String) = js.native
  }
}

package object hotjar {
  // https://help.hotjar.com/hc/en-us/articles/115011805448-Tracking-Virtual-Page-Views
  def pageView(page:String) = {
    // if hj is not defined, do nothing
    if(GlobalScope.asInstanceOf[js.Dynamic].hj.asInstanceOf[js.UndefOr[js.Dynamic]] != js.undefined) {
      GlobalScope.hj("vpv", page)
    }
  }
}


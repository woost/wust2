import org.scalajs.dom
import org.scalajs.dom.console
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
    def hj(what:String, args:js.Array[String]) = js.native
  }
}

package object hotjar {

  private def hotjarIsLoaded = GlobalScope.asInstanceOf[js.Dynamic].hj.asInstanceOf[js.UndefOr[js.Dynamic]] != js.undefined
  def pageView(page:String) = {
    // https://help.hotjar.com/hc/en-us/articles/115011805448-Tracking-Virtual-Page-Views
    // if hj is not defined, do nothing
    console.log(GlobalScope)
    if(hotjarIsLoaded) {
      println(s"hj defined, sending pageview $page")
      GlobalScope.hj("vpv", page)
    }
  }

  def tagRecording(tag:String) = {
    // https://help.hotjar.com/hc/en-us/articles/115011819488-Tagging-Recordings
    if(hotjarIsLoaded) {
      println(s"hj defined, sending tag $tag")
      GlobalScope.hj("tagRecording", js.Array(tag))
    }
  }
}


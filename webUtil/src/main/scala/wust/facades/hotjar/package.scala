package wust.facades

import scala.scalajs.js

package object hotjar {
  // https://help.hotjar.com/hc/en-us/articles/115011805448-Tracking-Virtual-Page-Views
  def pageView(page: String) = {
    // if hj is not defined, do nothing
    if (GlobalScope.asInstanceOf[js.Dynamic].hj.asInstanceOf[js.UndefOr[js.Dynamic]] != js.undefined) {
      GlobalScope.hj("vpv", page)
    }
  }
}

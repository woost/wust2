package wust.webApp.jsdom

import org.scalajs.dom
import scala.scalajs.js
import wust.webUtil.UI

object FormValidator {
  // wrapper around reportValidity/checkValidity. Some browsers (webview with safari) do not support reportValidity. We then fallback to checkValidity with a warning toast about invalid forms.
  // If checkValidity is also not defined, we just say the form is valid and let the backend do any checking.
  def reportValidity(form: dom.html.Form): Boolean = {
    def reportFun = form.asInstanceOf[js.Dynamic].reportValidity.asInstanceOf[js.UndefOr[js.Function0[Boolean]]].map(_.bind(form).asInstanceOf[js.Function0[Boolean]])
    def checkFun = form.asInstanceOf[js.Dynamic].checkValidity.asInstanceOf[js.UndefOr[js.Function0[Boolean]]].map(_.bind(form).asInstanceOf[js.Function0[Boolean]]).map { f =>
      { () =>
        val isValid = f()
        if (!isValid) UI.toast("Please check the input fields for correctness.", "This form is invalid", level = UI.ToastLevel.Warning)
        isValid
      }: js.Function0[Boolean]
    }

    val fun: js.Function0[Boolean] = reportFun.orElse(checkFun).getOrElse((() => true): js.Function0[Boolean])
    fun()
  }
}

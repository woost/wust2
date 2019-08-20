package wust.webApp.views

import org.scalajs.dom
import scala.scalajs.js

object DownloadHelper {
  def promptDownload(fileName: String, blob: dom.Blob): Unit = {
    val elem = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
    val url = dom.URL.createObjectURL(blob)
    elem.href = url
    elem.asInstanceOf[js.Dynamic].download = fileName
    elem.style.display = "none"
    dom.document.body.appendChild(elem)
    elem.click()
    dom.window.document.body.removeChild(elem)
    // dom.URL.revokeObjectUrl(url)
  }
}

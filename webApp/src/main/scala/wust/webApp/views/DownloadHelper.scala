package wust.webApp.views

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object DownloadHelper {

  // type: is type of data, e.g. text/csv, application/json
  // endings: 'transparent' (keep lineendings as is) or 'native' (convert to system lineendings)
  def promptDownload(fileName: String, data: String, `type`: Option[String] = None, endings: Option[String] = None): Unit = {
    promptDownload(
      fileName,
      new dom.Blob(
        js.Array(data),
        js.Dynamic.literal(`type` = `type`.orUndefined, endings = endings.orUndefined).asInstanceOf[dom.BlobPropertyBag]
      )
    )
  }

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

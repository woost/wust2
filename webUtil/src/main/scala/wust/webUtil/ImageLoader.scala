package wust.webUtil

import typings.chartDotJs.chartDotJsMod.ChartConfiguration
import cats.effect.IO
import fontAwesome.{ IconLookup, Params, Transform, fontawesome, freeSolid }
import wust.facades.fomanticui.AutoResizeConfig
import wust.facades.dateFns.DateFns
import wust.facades.hammerjs
import wust.facades.immediate.immediate
import monix.execution.Cancelable
import monix.reactive.{ Observable, Observer }
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{ HTMLElement, HTMLInputElement }
import org.scalajs.dom.window.{ clearTimeout, setTimeout }
import org.scalajs.dom.{ KeyboardEvent, MouseEvent }
import outwatch.ProHandler
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{ CustomEmitterBuilder, EmitterBuilder, PropBuilder, SyncEmitterBuilder }
import rx._
import wust.facades.emojijs.EmojiConvertor
import wust.facades.marked.Marked
import wust.facades.dompurify.DOMPurify
import wust.webUtil.outwatchHelpers._
import wust.ids.EpochMilli
import wust.util._

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.scalajs.js.`|`
import scala.scalajs.js.JSConverters._
import wust.facades.dompurify.DomPurifyConfig

import scala.concurrent.Promise
import scala.util.Try

@js.native
@JSImport("blueimp-load-image", JSImport.Namespace)
object loadImage extends js.Object {
  def apply(url:String, callback: js.Function2[HTMLElement, js.Object, Unit], options:js.Object):Unit = js.native
}

object ImageLoader {
  dom.console.log("AAAAA", loadImage)

  def apply(url: String, maxSize:Int): VDomModifier = {
    val imgTag = Promise[HTMLElement]
    loadImage.apply(url, { (loadedImg, data) =>
      dom.console.log("AAAAAData:",data.asInstanceOf[js.Dynamic].exif)
      Try{
        if(loadedImg.asInstanceOf[js.Dynamic].`type`.asInstanceOf[js.UndefOr[String]] != js.defined("error"))
          imgTag.success(loadedImg)
      }
    }:js.Function2[HTMLElement,js.Object, Unit], js.Dynamic.literal(maxHeight = maxSize, maxWidth = maxSize, cover = true, orientation = true))
    Observable.fromFuture(imgTag.future).map(imgTag => elemToVNode(imgTag, div))
  }

  @inline def elemToVNode(elem: HTMLElement, wrapper: VNode): VDomModifier = {
    wrapper(
      managedElement.asHtml{
        tagElem =>
          tagElem.appendChild(elem)
          Cancelable(() => tagElem.removeChild(elem))
      }
    )
  }
}

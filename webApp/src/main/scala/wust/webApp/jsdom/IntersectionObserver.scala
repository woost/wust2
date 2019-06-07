package wust.webApp.jsdom

import org.scalajs.dom
import org.scalajs.dom.experimental.Sequence

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|

// specification: https://w3c.github.io/IntersectionObserver/

@js.native
@JSGlobal
class IntersectionObserver(callback: js.Function2[Sequence[IntersectionObserverEntry], IntersectionObserver, Unit], options: IntersectionObserverOptions) extends js.Object {
  def root: js.UndefOr[dom.Element] = js.native
  def rootMargin: js.UndefOr[String] = js.native
  def thresholds: js.Array[Double] = js.native // TODO: frozen array?
  def observe(target: dom.Element): Unit = js.native
  def unobserve(target: dom.Element): Unit = js.native
  def disconnect(): Unit = js.native
  def takeRecords(): Sequence[IntersectionObserverEntry] = js.native
}

@js.native
trait IntersectionObserverEntry extends js.Object {
  def time: Double

  //TODO what are the corresponding types in scala.js?
  //DOMRectReadOnly? rootBounds;
  //DOMRectReadOnly boundingClientRect;
  //DOMRectReadOnly intersectionRect;

  def isIntersecting: Boolean = js.native
  def intersectionRatio: Double = js.native
  def target: dom.Element = js.native
}

trait IntersectionObserverOptions extends js.Object {
  var root: js.UndefOr[dom.Element] = js.undefined
  var rootMargin: js.UndefOr[String] = js.undefined
  var threshold: js.UndefOr[Double | Sequence[Double]] = js.undefined
}

package wust.webApp

import cats.Functor
import cats.effect.IO
import fontAwesome._
import jquery.JQuerySelection
import monix.execution.{Ack, Cancelable, CancelableFuture, Scheduler}
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.{console, document}
import outwatch.AsVDomModifier
import outwatch.dom.{AsObserver, AsValueObservable, BasicVNode, CompositeModifier, ConditionalVNode, Handler, Key, ManagedSubscriptions, ObservableWithInitialValue, OutWatch, ThunkVNode, VDomModifier, VNode, ValueObservable, dsl}
import outwatch.dom.helpers.{AsyncEmitterBuilder, EmitterBuilder}
import rx._
import wust.util.Empty
import wust.webUtil.macros.KeyHash

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import wust.util._
import scala.util.control.NonFatal

class Ownable[T](val get: Ctx.Owner => T) {
  @inline final def map[R](f: T => R): Ownable[R] = Ownable(get andThen f)
  @inline final def mapWithOwner[R](f: Ctx.Owner => T => R): Ownable[R] = Ownable(ctx => f(ctx)(get(ctx)))
  @inline final def flatMap[R](f: T => Ownable[R]): Ownable[R] = Ownable(ctx => f(get(ctx)).get(ctx))
}
object Ownable {
  @inline def apply[T](get: Ctx.Owner => T): Ownable[T] = new Ownable[T](get)

  implicit def asVDomModifier[T: AsVDomModifier]: AsVDomModifier[Ownable[T]] = ownable => outwatchHelpers.withManualOwner(ownable.get(_))
}

// TODO: outwatch: easily switch classes on and off via Boolean or Rx[Boolean]
//TODO: outwatch: onInput.target foreach { elem => ... }
//TODO: outwatch: Emitterbuilder.timeOut or delay
package object outwatchHelpers extends KeyHash {
  //TODO: it is not so great to have a monix scheduler and execution context everywhere, move to main.scala and pass through
  implicit val monixScheduler: Scheduler =
//    Scheduler.trampoline(executionModel = monix.execution.ExecutionModel.SynchronousExecution)
      Scheduler.global
//    Scheduler.trampoline(executionModel=AlwaysAsyncExecution)

  implicit object EmptyVDM extends Empty[VDomModifier] {
    @inline def empty: VDomModifier = VDomModifier.empty
  }

  implicit class RichVDomModifierFactory(val v: VDomModifier.type) extends AnyVal {
    @inline def ifTrue(condition:Boolean)(modifier: => VDomModifier):VDomModifier = if(condition) VDomModifier(modifier) else VDomModifier.empty
    @inline def ifTrue2(condition:Boolean)(modifier: => VDomModifier, modifier2: => VDomModifier):VDomModifier = if(condition) VDomModifier(modifier,modifier2) else VDomModifier.empty
    @inline def ifNot(condition:Boolean)(modifier: => VDomModifier):VDomModifier = if(!condition) VDomModifier(modifier) else VDomModifier.empty
  }

  implicit class RichVarFactory(val v: Var.type) extends AnyVal {
    @inline def empty[T: Empty]: Var[T] = Var(Empty[T])
  }

  implicit class RichRxFactory(val v: Rx.type) extends AnyVal {

    def merge[T](seed: T)(rxs: Rx[T]*)(implicit ctx: Ctx.Owner): Rx[T] = Rx.create(seed) { v =>
      rxs.foreach(_.triggerLater(v() = _))
    }
  }

  implicit class RichRx[T](val rx: Rx[T]) extends AnyVal {

    def toTailObservable: Observable[T] = Observable.create[T](Unbounded) { observer =>
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.triggerLater(observer.onNext(_))
      Cancelable(() => obs.kill())
    }

    def toValueObservable: ValueObservable[T] = () => ObservableWithInitialValue(Some(rx.now), rx.toTailObservable)

    def toRawObservable: Observable[T] = Observable.create[T](Unbounded) { observer =>
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.foreach(observer.onNext)
      Cancelable(() => obs.kill())
    }

    def toRawObservable[A](f: Ctx.Owner => Rx[T] => Rx[A]): Observable[A] = Observable.create[A](Unbounded) { observer =>
      implicit val ctx = createManualOwner()
      f(ctx)(rx).foreach(observer.onNext)(ctx)
      Cancelable(() => ctx.contextualRx.kill())
    }

    def tail(implicit ctx: Ctx.Owner): Rx[T] = Rx.create(rx.now) { v =>
      rx.triggerLater(v() = _)
    }

    @inline def subscribe(that: Var[T])(implicit ctx: Ctx.Owner): Obs = rx.foreach(that() = _)
    @inline def subscribe(that: Observer[T])(implicit ctx: Ctx.Owner): Obs = rx.foreach(that.onNext)

    @inline def debug(implicit ctx: Ctx.Owner): Obs = { debug() }
    @inline def debug(name: String = "")(implicit ctx: Ctx.Owner): Obs = {
      rx.debug(x => s"$name: $x")
    }
    @inline def debug(print: T => String)(implicit ctx: Ctx.Owner): Obs = {
      val boxBgColor = "#000" // HCL(baseHue, 50, 63).toHex
      val boxStyle =
        s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold; font-size:larger;"
//      val color = HCL(0, 0, 93).toHex // HCL(baseHue, 20, 93).toHex
      rx.foreach(x => console.log(s"%c ⟳ %c ${print(x)}", boxStyle, "background-color: transparent; font-weight: normal"))
    }

    @inline def debugWithDetail(print: T => String, detail: T => String)(implicit ctx: Ctx.Owner): Obs = {
      val boxBgColor = "#000" // HCL(baseHue, 50, 63).toHex
      val boxStyle =
        s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold; font-size:larger;"
      //      val color = HCL(0, 0, 93).toHex // HCL(baseHue, 20, 93).toHex
      rx.foreach{x =>
        console.asInstanceOf[js.Dynamic]
          .groupCollapsed(s"%c ⟳ %c ${print(x)}", boxStyle, "background-color: transparent; font-weight: normal")
        console.log(detail(x))
        console.asInstanceOf[js.Dynamic].groupEnd()
      }
    }

    //TODO: add to scala-rx in an efficient macro
    def collect[S](f: PartialFunction[T, S])(implicit ctx: Ctx.Owner): Rx[S] = rx.map(f.lift).filter(_.isDefined).map(_.get)
  }

  def createManualOwner(): Ctx.Owner = new Ctx.Owner(new Rx.Dynamic[Unit]((_,_) => (), None))
  def withManualOwner(f: Ctx.Owner => VDomModifier): VDomModifier = {
    val ctx = createManualOwner()
    VDomModifier(f(ctx), dsl.onDomUnmount foreach { ctx.contextualRx.kill() })
  }

  @inline implicit def obsToCancelable(obs: Obs): Cancelable = {
    Cancelable(() => obs.kill())
  }

  implicit def RxAsValueObservable: AsValueObservable[Rx] = new AsValueObservable[Rx] {
    @inline override def as[T](stream: Rx[T]): ValueObservable[T] = stream.toValueObservable
  }

  implicit object VarAsObserver extends AsObserver[Var] {
    @inline override def as[T](stream: Var[_ >: T]): Observer[T] = stream.toObserver
  }

  implicit class RichVar[T](val rxVar: Var[T]) extends AnyVal {
    @inline def toObserver: Observer[T] = new VarObserver(rxVar)
  }

  implicit class TypedElementsWithJquery[O <: dom.Element, R](val builder: EmitterBuilder[O, R]) extends AnyVal {
    def asJquery: EmitterBuilder[JQuerySelection, R] = builder.map { elem =>
      import jquery.JQuery._
      $(elem.asInstanceOf[dom.html.Element])
    }
  }

  implicit class ManagedElementsWithJquery(val builder: outwatch.dom.managedElement.type) extends AnyVal {
    def asJquery(subscription: JQuerySelection => Cancelable): VDomModifier = builder { elem =>
      import jquery.JQuery._
      subscription($(elem.asInstanceOf[dom.html.Element]))
    }
  }

  implicit class RichVNode(val vNode: BasicVNode) extends AnyVal {
    def staticRx(key: Key.Value)(renderFn: Ctx.Owner => VDomModifier): ConditionalVNode = vNode.static(key)(withManualOwner(renderFn))
    def thunkRx(key: Key.Value)(args: Any*)(renderFn: Ctx.Owner => VDomModifier): ThunkVNode = vNode.thunk(key)(args)(withManualOwner(renderFn))
    def render: org.scalajs.dom.Element = {
      val elem = document.createElement(vNode.nodeType)
      OutWatch.renderReplace(elem, vNode).unsafeRunSync()
      elem
    }
  }

  implicit class WustRichHandler[T](val o: Handler[T]) extends AnyVal {
    def unsafeToVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val v = o.value()
      val rx = Var[T](v.head.getOrElse(seed))
      v.tail.subscribe(rx)
      rx.triggerLater(o.onNext(_))
      rx
    }
  }

  implicit class WustRichObservable[T](val o: Observable[T]) extends AnyVal {
    //This is unsafe, as we leak the subscription here, this should only be done
    //for rx that are created only once in the app lifetime (e.g. in globalState)
    def unsafeToRx(seed: T): rx.Rx[T] = Rx.create(seed) { o.subscribe(_) }

    def subscribe(that: Var[T]): Cancelable = o.subscribe(new VarObserver[T](that))

    def onErrorThrow: Cancelable = o.subscribe(_ => Ack.Continue, throw _)
    def foreachTry(callback: T => Unit): CancelableFuture[Unit] = o.foreach { value =>
      try {
        callback(value)
      } catch {
        case e:Throwable => scribe.error(e.getMessage, e)
      }
    }

    def debug: Cancelable = debug()
    def debug(name: String = ""): CancelableFuture[Unit] = o.foreach(x => scribe.info(s"$name: $x"))
    def debug(print: T => String): CancelableFuture[Unit] = o.foreach(x => scribe.info(print(x)))
  }

  //TODO: Outwatch observable for specific key is pressed Observable[Boolean]
  def keyDown(keyCode: Int): Observable[Boolean] = Observable(
   outwatch.dom.dsl.events.document.onKeyDown.collect { case e if e.keyCode == keyCode => true },
   outwatch.dom.dsl.events.document.onKeyUp.collect { case e if e.keyCode == keyCode   => false },
 ).merge.startWith(false :: Nil)

  // fontawesome uses svg for icons and span for layered icons.
  // we need to handle layers as an html tag instead of svg.
  @inline private def stringToTag(tag: String): BasicVNode = if (tag == "span") dsl.htmlTag(tag) else dsl.svgTag(tag)
  @inline private def treeToModifiers(tree: AbstractElement): VDomModifier = VDomModifier(
    tree.attributes.map { case (name, value) => dsl.attr(name) := value }.toJSArray,
    tree.children.fold(js.Array[VNode]()) { _.map(abstractTreeToVNode) }
  )
  private def abstractTreeToVNode(tree: AbstractElement): VNode = {
    val tag = stringToTag(tree.tag)
    tag(treeToModifiers(tree))
  }
  private def abstractTreeToVNodeRoot(key: String, tree: AbstractElement): VNode = {
    val tag = stringToTag(tree.tag)
    tag.static(keyValue(key))(treeToModifiers(tree))
  }

  implicit def renderFontAwesomeIcon(icon: IconLookup): VNode = {
    abstractTreeToVNodeRoot(key = s"${icon.prefix}${icon.iconName}", fontawesome.icon(icon).`abstract`(0))
  }

  implicit def renderFontAwesomeObject(icon: FontawesomeObject): VNode = {
    abstractTreeToVNode(icon.`abstract`(0))
  }

  import scalacss.defaults.Exports.StyleA
  @inline implicit def styleToAttr(styleA: StyleA): VDomModifier = dsl.cls := styleA.htmlClass

  //TODO: add to fontawesome
  implicit class FontAwesomeOps(val fa: fontawesome.type) extends AnyVal {
    def layered(layers: Icon*): Layer = fa.layer(push => layers.foreach(push(_)))
  }

  def requestSingleAnimationFrame(): ( => Unit) => Unit = {
    var lastAnimationFrameRequest = -1
    f => {
      if(lastAnimationFrameRequest != -1) {
        dom.window.cancelAnimationFrame(lastAnimationFrameRequest)
      }
      lastAnimationFrameRequest = dom.window.requestAnimationFrame { _ =>
        f
      }
    }
  }

  def requestSingleAnimationFrame(code: => Unit): () => Unit = {
    val requester = requestSingleAnimationFrame()
    () => requester(code)
  }


  def inNextAnimationFrame[T](next: T => Unit): Observer[T] = new Observer.Sync[T] {
    private val requester = requestSingleAnimationFrame()
    override def onNext(elem: T): Ack = {
      requester(next(elem))
      Ack.Continue
    }
    override def onError(ex: Throwable): Unit = throw ex
    override def onComplete(): Unit = ()
  }

  //TODO AsEmitterBuilder type class in outwatch?
  @inline def emitterRx[T](rx: Rx[T]): EmitterBuilder[T, VDomModifier] = new RxEmitterBuilder[T](rx)
}

class VarObserver[T](rx: Var[T]) extends Observer.Sync[T] {
  override def onNext(elem: T): Ack = {
    rx() = elem
    Ack.Continue
  }
  override def onError(ex: Throwable): Unit = throw ex
  override def onComplete(): Unit = ()
}

trait RxEmitterBuilderBase[+O,+R] extends EmitterBuilder[O, R] {
  import outwatchHelpers._
  def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, R]
  @inline def map[T](f: O => T): EmitterBuilder[T, R] = transformRx[T](implicit ctx => _.map(f))
  @inline def filter(predicate: O => Boolean): EmitterBuilder[O, R] = transformRx[O](implicit ctx => _.filter(predicate))
  @inline def collect[T](f: PartialFunction[O, T]): EmitterBuilder[T, R] = mapOption(f.lift)
  @inline def mapOption[T](f: O => Option[T]): EmitterBuilder[T, R] = transformRx[T](implicit ctx => v => v.map(v => f(v)).filter(_.isEmpty).map(_.get))
}
class RxTransformingEmitterBuilder[E,O](rx: Rx[E], transformer: Ctx.Owner => Rx[E] => Rx[O]) extends RxEmitterBuilderBase[O, VDomModifier] {
  import outwatchHelpers._
  override def transform[T](tr: Observable[O] => Observable[T]): EmitterBuilder[T, VDomModifier] = EmitterBuilder.fromObservable[T](tr(rx.toRawObservable(transformer)))
  def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, VDomModifier] = new RxTransformingEmitterBuilder[E,T](rx, ctx => rx => tr(ctx)(transformer(ctx)(rx)))
  override def -->(observer: Observer[O]): VDomModifier = {
    outwatch.dom.managed { () =>
      implicit val ctx = createManualOwner()
      transformer(ctx)(rx).foreach(observer.onNext)(ctx)
      Cancelable(() => ctx.contextualRx.kill())
    }
  }
}
class RxEmitterBuilder[O](rx: Rx[O]) extends RxEmitterBuilderBase[O, VDomModifier] {
  import outwatchHelpers._
  override def transform[T](tr: Observable[O] => Observable[T]): EmitterBuilder[T, VDomModifier] = EmitterBuilder.fromObservable(tr(rx.toRawObservable))
  def transformRx[T](tr: Ctx.Owner => Rx[O] => Rx[T]): EmitterBuilder[T, VDomModifier] = new RxTransformingEmitterBuilder(rx, tr)
  override def -->(observer: Observer[O]): VDomModifier = {
    implicit val ctx = Ctx.Owner.Unsafe
    outwatch.dom.managed { () =>
      rx.foreach(observer.onNext)
    }
  }
}

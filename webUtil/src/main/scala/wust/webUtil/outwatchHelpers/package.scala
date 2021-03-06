package wust.webUtil

import cats.effect.SyncIO
import scala.scalajs.js.JSConverters._
import cats.Functor
import fontAwesome.{ AbstractElement, FontawesomeObject, IconLookup, fontawesome }
import wust.webUtil.macros.KeyHash
import monix.eval.Task
import monix.execution.{ Ack, CancelableFuture, Scheduler }
import monix.reactive.OverflowStrategy.Unbounded
import org.scalajs.dom
import org.scalajs.dom.{ console, document }
import rx._
import wust.facades.jquery.JQuerySelection
import wust.util.Empty

import outwatch._
import colibri._
import colibri.ext.monix._
import colibri.ext.rx._

import scala.concurrent.{ ExecutionContext, Future }
import scala.scalajs.js
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
import cats.effect.IO

package object outwatchHelpers extends KeyHash with RxInstances {
  //TODO: it is not so great to have a monix scheduler and execution context everywhere, move to main.scala and pass through
  implicit val monixScheduler: Scheduler =
       Scheduler.trampoline(executionModel = monix.execution.ExecutionModel.SynchronousExecution)
    // Scheduler.global
  //    Scheduler.trampoline(executionModel=AlwaysAsyncExecution)
  implicit val contextShift = IO.contextShift(monixScheduler)

  implicit object EmptyVDM extends Empty[VDomModifier] {
    @inline def empty: VDomModifier = VDomModifier.empty
  }

  implicit class RichVDomModifierFactory(val v: VDomModifier.type) extends AnyVal {
    @inline def ifTrue(condition: Boolean): ModifierBooleanOps = new ModifierBooleanOps(condition)
    @inline def ifNot(condition: Boolean): ModifierBooleanOps = new ModifierBooleanOps(!condition)
  }

  @inline implicit class RichFunctorVNode[F[_]: Functor](val f: F[VNode]) {
    @inline def apply(mods: VDomModifier*): F[VNode] = Functor[F].map(f)(_.apply(mods: _*))
    @inline def prepend(mods: VDomModifier*): F[VNode] = Functor[F].map(f)(_.prepend(mods: _*))
  }

  @inline implicit class RichFunctorVNodeNested[F[_]: Functor, G[_]: Functor](val f: F[G[VNode]]) {
    @inline def apply(mods: VDomModifier*): F[G[VNode]] = Functor[F].map(f)(g => Functor[G].map(g)(_.apply(mods: _*)))
    @inline def prepend(mods: VDomModifier*): F[G[VNode]] = Functor[F].map(f)(g => Functor[G].map(g)(_.apply(mods: _*)))
  }

  @inline implicit class RichVarFactory(val v: Var.type) extends AnyVal {
    @inline def empty[T: Empty]: Var[T] = Var(Empty[T])
  }

  implicit class RichRxFactory(val v: Rx.type) extends AnyVal {

    def fromFuture[T](seed: T)(future: Future[T], recover: PartialFunction[Throwable, T] = PartialFunction.empty): Rx[T] = Rx.create[T](seed) { rx =>
      future.onComplete {
        case Success(v) => rx() = v
        case Failure(t) => recover.lift(t) match {
          case Some(v) => rx() = v
          case None    => throw t
        }
      }
    }

    def merge[T](seed: T)(rxs: Rx[T]*)(implicit ctx: Ctx.Owner): Rx[T] = Rx.create(seed) { v =>
      rxs.foreach(_.triggerLater(v() = _))
    }
  }

  implicit class RichRx[T](val rx: Rx[T]) extends AnyVal {

    // This function will create a new rx that will stop triggering as soon as f(value) is None once.
    // If f(rx.now) is Some(value), we subscribe to rx and map every emitted value with f as long as it returns Some(value).
    // If f(rx.now) is None, we just return an rx that emits only the seed.
    def mapUntilEmpty[R](f: T => Option[R], seed: R)(implicit ctx: Ctx.Owner): Rx[R] = {
      f(rx.now) match {
        case Some(initialValue) =>
          val mappedRx = Var(initialValue)

          var sub: Obs = null
          sub = rx.triggerLater { value =>
            f(value) match {
              case Some(result) => mappedRx() = result
              case None         => sub.kill()
            }
          }

          mappedRx

        case None => Var(seed)
      }
    }

    def toTailMonixObservable: monix.reactive.Observable[T] = {
      monix.reactive.Observable.create[T](Unbounded) { observer =>
        implicit val ctx = Ctx.Owner.Unsafe

        val obs = rx.triggerLater(observer.onNext(_))

        monix.execution.Cancelable(() => obs.kill())
      }
    }

    def toMonixObservable: monix.reactive.Observable[T] = monix.reactive.Observable.create[T](Unbounded) { observer =>
      // transfer ownership from scala.rx to Monix. now Monix decides when the rx is killed.
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.foreach(observer.onNext)
      monix.execution.Cancelable(() => obs.kill())
    }

    def toObservable: Observable[T] = Observable.create[T] { observer =>
      // transfer ownership from scala.rx to Monix. now Monix decides when the rx is killed.
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.foreach(observer.onNext)
      Cancelable(() => obs.kill())
    }

    def toTailObservable: Observable[T] = Observable.create[T] { observer =>
      // transfer ownership from scala.rx to Monix. now Monix decides when the rx is killed.
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.triggerLater(observer.onNext(_))
      Cancelable(() => obs.kill())
    }

    @inline def subscribe[G[_]: Sink](that: G[T])(implicit ctx: Ctx.Owner): Obs = rx.foreach(Sink[G].onNext(that)(_))

    @inline def debug(implicit ctx: Ctx.Owner): Obs = { debug() }
    @inline def debug(name: String = "")(implicit ctx: Ctx.Owner): Obs = {
      rx.debug(x => s"$name: $x")
    }
    def debug(print: T => String)(implicit ctx: Ctx.Owner): Obs = {
      val boxBgColor = "#000" // HCL(baseHue, 50, 63).toHex
      val boxStyle =
        s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold; font-size:larger;"
      //      val color = HCL(0, 0, 93).toHex // HCL(baseHue, 20, 93).toHex
      rx.foreach(x => console.log(s"%c ⟳ %c ${print(x)}", boxStyle, "background-color: transparent; font-weight: normal"))
    }

    def debugWithDetail(print: T => String, detail: T => String)(implicit ctx: Ctx.Owner): Obs = {
      val boxBgColor = "#000" // HCL(baseHue, 50, 63).toHex
      val boxStyle =
        s"color: white; background: $boxBgColor; border-radius: 3px; padding: 2px; font-weight: bold; font-size:larger;"
      //      val color = HCL(0, 0, 93).toHex // HCL(baseHue, 20, 93).toHex
      rx.foreach{ x =>
        console.asInstanceOf[js.Dynamic]
          .groupCollapsed(s"%c ⟳ %c ${print(x)}", boxStyle, "background-color: transparent; font-weight: normal")
        console.log(detail(x))
        console.asInstanceOf[js.Dynamic].groupEnd()
      }
    }
  }

  def createManualOwner(): Ctx.Owner = new Ctx.Owner(new Rx.Dynamic[Unit]((_, _) => (), None))
  def withManualOwner(f: Ctx.Owner => VDomModifier): VDomModifier = {
    val ctx = createManualOwner()
    VDomModifier(f(ctx), dsl.onDomUnmount foreach { ctx.contextualRx.kill() })
  }

  implicit class TypedElementsWithJquery[O <: dom.Element, R](val builder: EmitterBuilder[O, R]) extends AnyVal {
    def asJquery: EmitterBuilder[JQuerySelection, R] = builder.map { elem =>
      import wust.facades.jquery.JQuery._
      $(elem.asInstanceOf[dom.html.Element])
    }
  }

  implicit class ManagedElementsWithJquery(val builder: outwatch.managedElement.type) extends AnyVal {
    def asJquery[T : CanCancel](subscription: JQuerySelection => T): VDomModifier = builder { elem =>
      import wust.facades.jquery.JQuery._
      subscription($(elem.asInstanceOf[dom.html.Element]))
    }
  }

  implicit class RichVNode(val vNode: VNode) extends AnyVal {
    def render: org.scalajs.dom.Element = (for {
      proxy <- OutWatch.toSnabbdom[SyncIO](vNode)
      //TODO outwatch: allow to render a VNodeProxy directly.
      _ <- OutWatch.renderReplace[SyncIO](document.createElement("div"), dsl.div(new VNodeProxyNode(proxy)))
    } yield proxy.elm.get).unsafeRunSync() // TODO

  }

  @inline implicit class RichRxVNode(val vNode: Rx[VNode]) extends AnyVal {
    @inline def append(m: VDomModifier*)(implicit ctx: Ctx.Owner) = vNode.map(_.apply(m: _*))
  }

  @inline implicit class RichObservableVNode(val vNode: Observable[VNode]) extends AnyVal {
    @inline def append(m: VDomModifier*) = vNode.map(_.apply(m: _*))
  }

  @inline implicit class WustRichHandler[F[_] : Source : Sink, T](val o: F[T]) {
    @inline def unsafeToVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = unsafeHandlerToVar(o)(seed)
  }

  private def unsafeHandlerToVar[F[_] : Source : Sink, T](o: F[T])(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
    val rx = Var[T](seed)
    Source[F].subscribe(o)(rx)
    rx.triggerLater(Sink[F].onNext(o)(_))
    rx
  }

  implicit class WustRichObservable[T](val o: Observable[T]) extends AnyVal {
    //This is unsafe, as we leak the subscription here, this should only be done
    //for rx that are created only once in the app lifetime (e.g. in globalState)
    def unsafeToRx(seed: T): rx.Rx[T] = Rx.create(seed) { o.subscribe(_) }
  }

  implicit class WustRichMonixObservable[T](val o: monix.reactive.Observable[T]) extends AnyVal {
    //This is unsafe, as we leak the subscription here, this should only be done
    //for rx that are created only once in the app lifetime (e.g. in globalState)
    def unsafeToRx(seed: T): rx.Rx[T] = Rx.create(seed) { subscribe(_) }

    def subscribe[G[_] : Sink](that: G[T]): monix.execution.Cancelable = o.subscribe(value => { Sink[G].onNext(that)(value); Ack.Continue }, Sink[G].onError(that)(_))

    def onErrorThrow: monix.execution.Cancelable = o.subscribe(_ => Ack.Continue, throw _)
    def foreachSafe(callback: T => Unit): monix.execution.Cancelable = o.map(callback).onErrorRecover{ case NonFatal(e) => scribe.warn(e); Unit }.subscribe()

    def debug: CancelableFuture[Unit] = debug()
    def debug(name: String = ""): CancelableFuture[Unit] = o.foreach(x => scribe.info(s"$name: $x"))
    def debug(print: T => String): CancelableFuture[Unit] = o.foreach(x => scribe.info(print(x)))
  }

  //TODO: Outwatch observable for specific key is pressed Observable[Boolean]
  def keyDown(keyCode: Int): Observable[Boolean] = Observable.merge(
    outwatch.dsl.events.document.onKeyDown.collect { case e if e.keyCode == keyCode => true },
    outwatch.dsl.events.document.onKeyUp.collect { case e if e.keyCode == keyCode => false }
  ).prepend(false)

  import scalacss.defaults.Exports.StyleA
  @inline implicit def styleToAttr(styleA: StyleA): VDomModifier = dsl.cls := styleA.htmlClass

  def requestSingleAnimationFrame(): (=> Unit) => Unit = {
    var lastAnimationFrameRequest = -1
    f => {
      if (lastAnimationFrameRequest != -1) {
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

  def inNextAnimationFrame[T](next: T => Unit): Observer[T] = {
    val requester = requestSingleAnimationFrame()
    Observer.create[T](elem => requester(next(elem)))
  }

  implicit class RichEmitterBuilderEvent[R](val builder: EmitterBuilder[dom.Event, R]) extends AnyVal {
    def onlyOwnEvents: EmitterBuilder[dom.Event, R] = builder.filter(ev => ev.currentTarget == ev.target)
  }

  def abstractTreeToVNodeRoot(key: String, tree: AbstractElement): VNode = {
    val tag = stringToTag(tree.tag)
    tag.thunkStatic(uniqueKey(key))(treeToModifiers(tree))
  }

  implicit def renderFontAwesomeIcon(icon: IconLookup): VNode = {
    abstractTreeToVNodeRoot(key = s"${icon.prefix}${icon.iconName}", fontawesome.icon(icon).`abstract`(0))
  }

  // fontawesome uses svg for icons and span for layered icons.
  // we need to handle layers as an html tag instead of svg.
  private def stringToTag(tag: String): BasicVNode = if (tag == "span") dsl.htmlTag(tag) else dsl.svgTag(tag)

  def treeToModifiers(tree: AbstractElement): VDomModifier = VDomModifier(
    tree.attributes.map { case (name, value) => dsl.attr(name) := value }.toJSArray,
    tree.children.fold(js.Array[VNode]()) { _.map(abstractTreeToVNode) }
  )

  private def abstractTreeToVNode(tree: AbstractElement): VNode = {
    val tag = stringToTag(tree.tag)
    tag(treeToModifiers(tree))
  }

  @inline implicit def renderFontAwesomeObject(icon: FontawesomeObject): VNode = {
    abstractTreeToVNode(icon.`abstract`(0))
  }
}

package wust.webUtil

import cats.effect.SyncIO
import scala.scalajs.js.JSConverters._
import cats.Functor
import fontAwesome.{ AbstractElement, FontawesomeObject, IconLookup, fontawesome }
import wust.webUtil.macros.KeyHash
import monix.eval.Task
import monix.execution.{ Ack, Cancelable, CancelableFuture, Scheduler }
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.{ Observable, Observer }
import org.scalajs.dom
import org.scalajs.dom.{ console, document }
import rx._
import wust.facades.jquery.JQuerySelection
import wust.util.Empty

import outwatch.dom._
import outwatch.reactive._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.ext.monix.handler._
import outwatch.ext.monix._

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

  implicit class RichVarFactory(val v: Var.type) extends AnyVal {
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

    def toLazyTailObservable: Observable[T] = {
      Observable.create[T](Unbounded) { observer =>
        implicit val ctx = Ctx.Owner.Unsafe

        val obs = rx.triggerLater(observer.onNext(_))

        Cancelable(() => obs.kill())
      }
    }

    def toTailObservable: Observable[T] = {
      val callNow = rx.now // now at call-time
      Observable.create[T](Unbounded) { observer =>
        implicit val ctx = Ctx.Owner.Unsafe

        // workaround: push now into the observer if it changed in between
        // calling this method and the observable being subscribed.
        // TODO: better alternative?
        // - maybe just triggerLater with implicit owner at call-time and push into ReplaySubject(limit = 1)?
        if (rx.now != callNow) observer.onNext(rx.now)
        val obs = rx.triggerLater(observer.onNext(_))

        Cancelable(() => obs.kill())
      }
    }

    def toObservable: Observable[T] = Observable.create[T](Unbounded) { observer =>
      // transfer ownership from scala.rx to Monix. now Monix decides when the rx is killed.
      implicit val ctx = Ctx.Owner.Unsafe
      val obs = rx.foreach(observer.onNext)
      Cancelable(() => obs.kill())
    }

    def toObservable[A](f: Ctx.Owner => Rx[T] => Rx[A]): Observable[A] = Observable.create[A](Unbounded) { observer =>
      implicit val ctx = createManualOwner()
      f(ctx)(rx).foreach(observer.onNext)(ctx)
      Cancelable(() => ctx.contextualRx.kill())
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

  implicit object obsCancelSubscription extends CancelSubscription[Obs] {
    def cancel(obs: Obs) = obs.kill()
  }

  implicit object RxSource extends Source[Rx] {
    @inline def subscribe[G[_] : Sink, A](stream: Rx[A])(sink: G[_ >: A]): Subscription = {
      implicit val ctx = Ctx.Owner.Unsafe
      Sink[G].onNext(sink)(stream.now)
      val obs = stream.triggerLater(Sink[G].onNext(sink)(_))
      Subscription(() => obs.kill())
    }
  }

  implicit object VarSink extends Sink[Var] {
    @inline override def onNext[A](sink: Var[A])(value: A): Unit = sink() = value
    @inline override def onError[A](sink: Var[A])(ex: Throwable): Unit = throw ex //TODO notify somebody
  }

  implicit class TypedElementsWithJquery[O <: dom.Element, R](val builder: EmitterBuilder[O, R]) extends AnyVal {
    def asJquery: EmitterBuilder[JQuerySelection, R] = builder.map { elem =>
      import wust.facades.jquery.JQuery._
      $(elem.asInstanceOf[dom.html.Element])
    }
  }

  implicit class ManagedElementsWithJquery(val builder: outwatch.dom.managedElement.type) extends AnyVal {
    def asJquery(subscription: JQuerySelection => Cancelable): VDomModifier = builder { elem =>
      import wust.facades.jquery.JQuery._
      subscription($(elem.asInstanceOf[dom.html.Element]))
    }
  }

  implicit class RichVNode(val vNode: VNode) extends AnyVal {
    def render: org.scalajs.dom.Element = (for {
      proxy <- OutWatch.toSnabbdom[SyncIO](vNode)
      //TODO outwatch: allow to render a VNodeProxy directly.
      _ <- OutWatch.renderReplace[SyncIO](document.createElement("div"), dsl.div(VNodeProxyNode(proxy)))
    } yield proxy.elm.get).unsafeRunSync() // TODO 

    @inline def append(m: VDomModifier*) = vNode.apply(m: _*)
  }

  implicit class RichRxVNode(val vNode: Rx[VNode]) extends AnyVal {
    @inline def append(m: VDomModifier*)(implicit ctx: Ctx.Owner) = vNode.map(_.apply(m: _*))
  }

  implicit class RichObservableVNode(val vNode: Observable[VNode]) extends AnyVal {
    @inline def append(m: VDomModifier*) = vNode.map(_.apply(m: _*))
  }

  implicit class WustRichHandler[T](val o: Handler[T]) extends AnyVal {
    def unsafeToVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val rx = Var[T](seed)
      o.subscribe(rx)
      rx.triggerLater(o.onNext(_))
      rx
    }

    @inline def transformObserverWith[R](f: Observer[T] => Observer[R]): ProHandler[R, T] = ProHandler(f(o), o)
  }

  implicit class WustRichObserver[T](val o: Observer[T]) extends AnyVal {
    //TODO: helper in outwatch monixops for redirectFuture
    @inline def redirectEval[R](f: R => Task[T]): Observer[R] = redirectFuture(r => f(r).runToFuture)
    def redirectFuture[R](f: R => Future[T]): Observer[R] = new Observer[R] {
      override def onNext(elem: R): Future[Ack] = f(elem).flatMap(o.onNext(_))
      override def onError(ex: Throwable): Unit = o.onError(ex)
      override def onComplete(): Unit = o.onComplete()
    }
  }

  implicit class WustRichSourceStream[T](val o: SourceStream[T]) extends AnyVal {
    //This is unsafe, as we leak the subscription here, this should only be done
    //for rx that are created only once in the app lifetime (e.g. in globalState)
    def unsafeToRx(seed: T): rx.Rx[T] = Rx.create(seed) { o.subscribe(_) }
  }

  implicit class WustRichObservable[T](val o: Observable[T]) extends AnyVal {
    //This is unsafe, as we leak the subscription here, this should only be done
    //for rx that are created only once in the app lifetime (e.g. in globalState)
    def unsafeToRx(seed: T): rx.Rx[T] = Rx.create(seed) { o.subscribe(_) }

    def subscribe(that: Var[T]): Cancelable = o.subscribe(new VarObserver[T](that))

    def onErrorThrow: Cancelable = o.subscribe(_ => Ack.Continue, throw _)
    def foreachSafe(callback: T => Unit): Cancelable = o.map(callback).onErrorRecover{ case NonFatal(e) => scribe.warn(e); Unit }.subscribe()

    def debug: Cancelable = debug()
    def debug(name: String = ""): CancelableFuture[Unit] = o.foreach(x => scribe.info(s"$name: $x"))
    def debug(print: T => String): CancelableFuture[Unit] = o.foreach(x => scribe.info(print(x)))
  }

  //TODO: Outwatch observable for specific key is pressed Observable[Boolean]
  def keyDown(keyCode: Int): SourceStream[Boolean] = SourceStream.merge(
    outwatch.dom.dsl.events.document.onKeyDown.collect { case e if e.keyCode == keyCode => true },
    outwatch.dom.dsl.events.document.onKeyUp.collect { case e if e.keyCode == keyCode => false }
  ).prepend(false)

  @inline def multiObserver[T](observers: Observer[T]*): Observer[T] = new CombinedObserver[T](observers)

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

  def inNextAnimationFrame[T](next: T => Unit): Observer[T] = new Observer.Sync[T] {
    private val requester = requestSingleAnimationFrame()
    override def onNext(elem: T): Ack = {
      requester(next(elem))
      Ack.Continue
    }
    override def onError(ex: Throwable): Unit = throw ex
    override def onComplete(): Unit = ()
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
  @inline private def stringToTag(tag: String): BasicVNode = if (tag == "span") dsl.htmlTag(tag) else dsl.svgTag(tag)

  @inline private def treeToModifiers(tree: AbstractElement): VDomModifier = VDomModifier(
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

@inline class VarObserver[T](rx: Var[T]) extends Observer.Sync[T] {
  @inline override def onNext(elem: T): Ack = {
    rx() = elem
    Ack.Continue
  }
  @inline override def onError(ex: Throwable): Unit = throw ex
  @inline override def onComplete(): Unit = ()
}

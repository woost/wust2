package wust.webApp

import cats.effect.IO
import com.raquo.domtypes.generic.keys.Style
import fontAwesome._
import monix.execution.{Ack, Cancelable, CancelableFuture, Scheduler}
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.{BehaviorSubject, ReplaySubject}
import org.scalajs.dom
import org.scalajs.dom.{Element, document}
import outwatch.dom.helpers.{AttributeBuilder, CustomEmitterBuilder, EmitterBuilder}
import outwatch.dom.{AsObserver, AsValueObservable, Attribute, Handler, Modifier, ModifierStreamReceiver, OutWatch, VDomModifier, VNode, ValueObservable, dsl}
import outwatch.{AsVDomModifier, Sink}
import rx._
import wust.util.Empty

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

//TODO nicer name
package object outwatchHelpers {
  //TODO: it is not so great to have a monix scheduler and execution context everywhere, move to main.scala and pass through
  implicit val monixScheduler: Scheduler =
//    Scheduler.trampoline(executionModel = monix.execution.ExecutionModel.SynchronousExecution)
      Scheduler.global
//    Scheduler.trampoline(executionModel=AlwaysAsyncExecution)

  implicit class RichVarFactory(val v: Var.type) extends AnyVal {
    def empty[T: Empty]: Var[T] = Var(Empty[T])
  }

  implicit class RichRxFactory(val v: Rx.type) extends AnyVal {

    def merge[T](seed: T)(rxs: Rx[T]*)(implicit ctx: Ctx.Owner): Rx[T] = Rx.create(seed) { v =>
      rxs.foreach(_.triggerLater(v() = _))
    }
  }


  implicit class RichHandlerFactory(val v: Handler.type) extends AnyVal {
    def created[T]:Handler[T] = ReplaySubject.createLimited(1)

    def created[T](seed:T):Handler[T] = BehaviorSubject[T](seed)
  }

  //TODO toObservable/toVar/toRx are methods should be done once and with care. Therefore they should not be in an implicit class on the instance, but in an extra factory like ReactiveConverters.observable/rx/var
  implicit class RichRx[T](val rx: Rx[T]) extends AnyVal {
    def toLaterObservable(implicit ctx: Ctx.Owner): Observable[T] = Observable.create[T](Unbounded) {
      observer =>
        val obs = rx.triggerLater(observer.onNext(_))
        Cancelable(() => obs.kill())
    }

    def toObservable(implicit ctx: Ctx.Owner): Observable[T] = Observable.create[T](Unbounded) {
      observer =>
        val obs = rx.foreach(observer.onNext)
        Cancelable(() => obs.kill())
    }

    def tail(implicit ctx: Ctx.Owner): Rx[T] = Rx.create(rx.now) { v =>
      rx.triggerLater(v() = _)
    }

    def subscribe(that: Var[T])(implicit ctx: Ctx.Owner): Obs = rx.foreach(that() = _)
    def subscribe(that: Observer[T])(implicit ctx: Ctx.Owner): Obs = rx.foreach(that.onNext)

    def debug(implicit ctx: Ctx.Owner): Obs = { debug() }
    def debug(name: String = "")(implicit ctx: Ctx.Owner): Obs = {
      rx.foreach(x => println(s"$name: $x"))
    }
    def debug(print: T => String)(implicit ctx: Ctx.Owner): Obs = {
      rx.foreach(x => println(print(x)))
    }

    //TODO: add to scala-rx in an efficient macro
    def collect[S](f: PartialFunction[T, S])(implicit ctx: Ctx.Owner): Rx[S] = rx.filter(f.isDefinedAt _).map(f)
  }

  implicit def obsToCancelable(obs: Obs): Cancelable = {
    Cancelable(() => obs.kill())
  }

  implicit def RxAsValueObservable(implicit ctx:Ctx.Owner): AsValueObservable[Rx] = new AsValueObservable[Rx] {
    override def as[T](stream: Rx[T]): ValueObservable[T] = new ValueObservable[T] {
      override def observable: Observable[T] = stream.toLaterObservable
      override def value: Option[T] = Some(stream.now)
    }
  }

  implicit object VarAsObserver extends AsObserver[Var] {
    override def as[T](stream: Var[_ >: T]): Observer[T] = stream.toObserver
  }

  implicit class RichVar[T](val rxVar: Var[T]) extends AnyVal {
    def toObserver: Observer[T] = new VarObserver(rxVar)
  }

  implicit class RichVNode(val vNode: VNode) {
    def render: org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      OutWatch.renderReplace(elem, vNode).unsafeRunSync()
      elem
    }
  }

  implicit class WustRichHandler[T](val o: Handler[T]) extends AnyVal {
    def unsafeToVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val rx = Var[T](seed)
      o.subscribe(rx)
      rx.foreach(o.onNext)
      rx
    }
  }

  implicit class WustRichObservable[T](val o: Observable[T]) extends AnyVal {
    //This is unsafe, as we leak the subscription here, this should only be done
    //for rx that are created only once in the app lifetime (e.g. in globalState)
    def unsafeToRx(seed: T): rx.Rx[T] = {
      val rx = Var[T](seed)
      o.subscribe(rx)
      rx
    }

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
    def debug(name: String = ""): CancelableFuture[Unit] = o.foreach(x => println(s"$name: $x"))
    def debug(print: T => String): CancelableFuture[Unit] = o.foreach(x => println(print(x)))
  }

  //TODO: Outwatch observable for specific key is pressed Observable[Boolean]
  def keyDown(keyCode: Int): Observable[Boolean] = Observable.merge(
    outwatch.dom.dsl.events.document.onKeyDown.collect { case e if e.keyCode == keyCode => true },
    outwatch.dom.dsl.events.document.onKeyUp.collect { case e if e.keyCode == keyCode   => false },
  ).startWith(false :: Nil)

  private def abstractTreeToVNode(tree: AbstractElement): VNode = {
    import outwatch.dom.dsl.{attr, tag}
    tag(tree.tag)(
      //TODO return js array in facade?
      tree.attributes.map { case (name, value) => attr(name) := value }.toJSArray,
      tree.children.fold(js.Array[VNode]()) { _.map(abstractTreeToVNode) }
    )
  }

  implicit def renderFontAwesomeIcon(icon: IconLookup): VNode = {
    abstractTreeToVNode(fontawesome.icon(icon).`abstract`(0))
  }

  implicit def renderFontAwesomeObject(icon: FontawesomeObject): VNode = {
    abstractTreeToVNode(icon.`abstract`(0))
  }

  import scalacss.defaults.Exports.StyleA
  implicit def styleToAttr(styleA: StyleA): VDomModifier = dsl.cls := styleA.htmlClass

  implicit object VDomModifierEmpty extends Empty[VDomModifier] {
    def empty: VDomModifier = VDomModifier.empty
  }


  //TODO: put in outwatch
  implicit class TypedElements[E <: Element, H](builder: EmitterBuilder[E, E, H]) {
    def asHtml: EmitterBuilder[E, dom.html.Element, H] = builder.map(_.asInstanceOf[dom.html.Element])

    def asSvg: EmitterBuilder[E, dom.svg.Element, H] = builder.map(_.asInstanceOf[dom.svg.Element])
  }

  //TODO: add to fontawesome
  implicit class FontAwesomeOps(val fa: fontawesome.type) extends AnyVal {
    def layered(layers: Icon*): Layer = fa.layer(push => layers.foreach(push(_)))
  }

  def keyed(implicit file: sourcecode.File, line: sourcecode.Line, column: sourcecode.Column): VDomModifier = keyed(Nil)
  def keyed(keys: Any*)(implicit file: sourcecode.File, line: sourcecode.Line, column: sourcecode.Column): VDomModifier = {
    val parts = s"${file.value}:${line.value}:${column.value}" +: keys
    val keyNumber = parts.hashCode
    val keyModifier = dsl.key := keyNumber
    if (DevOnly.isTrue) VDomModifier(keyModifier, dsl.data.key := keyNumber)
    else keyModifier
  }
}

class VarObserver[T](rx: Var[T]) extends Observer.Sync[T] {
  override def onNext(elem: T): Ack = {
    rx() = elem
    Ack.Continue
  }
  override def onError(ex: Throwable): Unit = throw ex
  override def onComplete(): Unit = ()
}

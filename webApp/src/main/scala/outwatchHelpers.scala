package wust.webApp

import cats.effect.IO
import com.raquo.domtypes.generic.keys.Style
import fontAwesome._
import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.{Observable, Observer}
import monix.reactive.OverflowStrategy.Unbounded
import org.scalajs.dom.document
import outwatch.dom.helpers.{AttributeBuilder, EmitterBuilder}
import outwatch.dom.{Attribute, Handler, OutWatch, VDomModifier, VNode, dsl, ModifierStreamReceiver}
import outwatch.{AsVDomModifier, ObserverSink, Sink}
import rx._

import scala.collection.breakOut
import scala.concurrent.Future
// Outwatch TODOs:
// when writing: sink <-- obs; obs(action)
// action is always triggered first, even though it is registered after subscribe(<--)
//
// observable.filter does not accept partial functions.filter{case (_,text) => text.nonEmpty}
//

//TODO nicer name
package object outwatchHelpers {
  //TODO: it is not so great to have a monix scheduler and execution context everywhere, move to main.scala and pass through
  implicit val monixScheduler: Scheduler =
    Scheduler.trampoline(executionModel = SynchronousExecution)

  //TODO toObservable/toVar/toRx are methods should be done once and with care. Therefore they should not be in an implicit class on the instance, but in an extra factory like ReactiveConverters.observable/rx/var
  implicit class RichRx[T](val rx: Rx[T]) extends AnyVal {
    def toLaterObservable(implicit ctx: Ctx.Owner): Observable[T] = Observable.create[T](Unbounded) {
      observer =>
        rx.triggerLater { value =>
          println("value " + value)
          observer.onNext(value)
        }
        Cancelable() //TODO
    }

    def toObservable(implicit ctx: Ctx.Owner): Observable[T] = Observable.create[T](Unbounded) {
      observer =>
        rx.foreach(observer.onNext)
        Cancelable() //TODO
    }

    def debug(implicit ctx: Ctx.Owner): Rx[T] = { debug() }
    def debug(name: String = "")(implicit ctx: Ctx.Owner): Rx[T] = {
      rx.foreach(x => println(s"$name: $x"))
      rx
    }
    def debug(print: T => String)(implicit ctx: Ctx.Owner): Rx[T] = {
      rx.foreach(x => println(print(x)))
      rx
    }
  }

  implicit def observerAsSink[T](observer: Observer[T])(implicit ctx: Ctx.Owner): Sink[T] =
    ObserverSink(observer)

  implicit def rxAsVDomModifier[T: AsVDomModifier](implicit ctx: Ctx.Owner): AsVDomModifier[Rx[T]] =
    (value: Rx[T]) => VDomModifier(value.now).map(current => ModifierStreamReceiver(value.toLaterObservable.map(VDomModifier(_)), current))
  // implicit def rxSeqAsVDomModifier[T: AsVDomModifier](
  //     implicit ctx: Ctx.Owner
  // ): AsVDomModifier[Rx[Seq[T]]] = (value: Rx[Seq[T]]) => ModifierStreamReceiver(value.toLaterObservable, rx.now.unsafeRunSync)
  // implicit def rxOptionAsVDomModifier[T: AsVDomModifier](
  //     implicit ctx: Ctx.Owner
  // ): AsVDomModifier[Rx[Option[T]]] = (value: Rx[Option[T]]) => value.toObservable
  implicit class RichEmitterBuilder[E, O, R](val eb: EmitterBuilder[E, O, R]) extends AnyVal {
    //TODO: scala.rx have a contravariant trait for writing-only
    def -->(rxVar: Var[_ >: O])(implicit ctx: Ctx.Owner): IO[R] = eb --> rxVar.toSink
  }
  implicit class RichAttributeEmitterBuilder[-T, +A <: Attribute](val ab: AttributeBuilder[T, A])
      extends AnyVal {
    def <--(valueStream: Rx[T])(implicit ctx: Ctx.Owner) = ab <-- valueStream.toObservable
  }
  implicit class RichStyle[T](val ab: Style[T]) extends AnyVal {
    import outwatch.dom.StyleIsBuilder
    //TODO: make outwatch AttributeStreamReceiver public to allow these kinds of builder conversions?
    def <--(valueStream: Rx[T])(implicit ctx: Ctx.Owner) =
      StyleIsBuilder[T](ab) <-- valueStream.toObservable
  }

  implicit class RichVar[T](val rxVar: Var[T]) extends AnyVal {
    def toHandler(implicit ctx: Ctx.Owner): Handler[T] = {

      val h = Handler.create[T](rxVar.now).unsafeRunSync()
      h.filter(_ != rxVar.now).foreach(rxVar.update)
      rxVar.foreach(h.unsafeOnNext)
      h
    }

    def toSink(implicit ctx: Ctx.Owner): Sink[T] = {

      Sink
        .create[T] { event =>
          rxVar.update(event)
          Ack.Continue
        }
        .unsafeRunSync()
    }
  }

  implicit class RichVNode(val vNode: VNode) {
    def render: org.scalajs.dom.Element = {
      val elem = document.createElement("div")
      OutWatch.renderReplace(elem, vNode).unsafeRunSync()
      elem
    }
  }

  implicit class RichHandler[T](val o: Handler[T]) extends AnyVal {
    def toVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val rx = Var[T](seed)
      o.foreach(rx.update)
      rx.foreach(o.unsafeOnNext)
      rx
    }
  }

  implicit class RichSink[T](val o: Sink[T]) extends AnyVal {
    def toVar(seed: T)(implicit ctx: Ctx.Owner): rx.Var[T] = {
      val rx = Var[T](seed)
      rx.foreach(o.unsafeOnNext)
      rx
    }
  }

  implicit class RichObservable[T](val o: Observable[T]) extends AnyVal {
    def toRx(seed: T)(implicit ctx: Ctx.Owner): rx.Rx[T] = {
      val rx = Var[T](seed)
      o.subscribe(new Observer.Sync[T] {
        override def onNext(elem: T): Ack = {
          rx() = elem
          Ack.Continue
        }
        override def onError(ex: Throwable): Unit = throw ex
        override def onComplete(): Unit = ()
      })
      rx
    }

    def debug: Cancelable = debug()
    def debug(name: String = "") = o.foreach(x => println(s"$name: $x"))
    def debug(print: T => String) = o.foreach(x => println(print(x)))
  }

  //TODO: Outwatch observable for specific key is pressed Observable[Boolean]
  def keyDown(keyCode: Int): Observable[Boolean] = Observable.merge(
    outwatch.dom.dsl.events.window.onKeyDown.collect { case e if e.keyCode == keyCode => true },
    outwatch.dom.dsl.events.window.onKeyUp.collect { case e if e.keyCode == keyCode   => false },
  )

  private def abstractTreeToVNode(tree: AbstractElement): VNode = {
    import outwatch.dom.dsl.{attr, tag}
    tag(tree.tag)(
      tree.attributes
        .map { case (name, value) => attr(name) := value }(breakOut): Seq[VDomModifier],
      tree.children.fold(Seq.empty[VNode]) { _.map(abstractTreeToVNode) }
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
}

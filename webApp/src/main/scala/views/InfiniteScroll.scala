package wust.webApp.views

import cats.effect.IO
import monix.execution.Cancelable
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx.{Ctx, Rx}
import wust.css.Styles
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.outwatchHelpers._

object InfiniteScroll {

  val onIntersectionWithViewport: EmitterBuilder[Boolean, VDomModifier] = onIntersectionWithViewport(ignoreInitial = false)
  def onIntersectionWithViewport(ignoreInitial: Boolean): EmitterBuilder[Boolean, VDomModifier] =
    EmitterBuilder.ofModifier[Boolean] { sink => IO {
      var prevIsIntersecting = ignoreInitial

      VDomModifier(
        managedElement.asHtml { elem =>
          // TODO: does it make sense to only have one intersection observer?
          val observer = new IntersectionObserver(
            { (entry, obs) =>
              val isIntersecting = entry.head.isIntersecting
              if (isIntersecting != prevIsIntersecting) {
                sink.onNext(isIntersecting)
                prevIsIntersecting = isIntersecting
              }
            },
            new IntersectionObserverOptions {
              root = elem.parentElement
              rootMargin = "100px 0px 0px 0px"
              threshold = 0
            }
          )

          observer.observe(elem)

          Cancelable { () =>
            observer.unobserve(elem)
            observer.disconnect()
          }
        }
      )
    }}

  def onInfiniteScrollUp(shouldLoad: Rx[Boolean])(implicit ctx: Ctx.Owner): EmitterBuilder[Int, VDomModifier] =
    EmitterBuilder.ofModifier[Int] { sink => IO {

      var lastHeight = 0.0
      var lastScrollTop = 0.0
      var numSteps = 0

      VDomModifier(
        overflow.auto,
        shouldLoad.map {
          case true => VDomModifier(
            div(
              div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, Components.woostLoadingAnimation),
              onIntersectionWithViewport(ignoreInitial = false).foreach { isIntersecting =>
                if (isIntersecting) {
                  numSteps += 1
                  sink.onNext(numSteps)
                }
              }
            ),
            onDomPreUpdate.asHtml.foreach { elem =>
              lastScrollTop = elem.scrollTop
            },
            onDomUpdate.asHtml.foreach { elem =>
              if (elem.scrollHeight > lastHeight) {
                val diff = elem.scrollHeight - lastHeight
                lastHeight = elem.scrollHeight
                elem.scrollTop = diff + lastScrollTop
              }
            }
          )
        case false => VDomModifier.empty
        }
      )
    }}
}

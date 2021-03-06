package wust.webApp.views

import org.scalajs.dom
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
import rx.{Ctx, Rx}
import wust.css.Styles
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webUtil.outwatchHelpers._

object InfiniteScroll {

  val onIntersectionWithViewport: EmitterBuilder[Boolean, VDomModifier] = onIntersectionWithViewport(ignoreInitial = false)
  def onIntersectionWithViewport(ignoreInitial: Boolean): EmitterBuilder[Boolean, VDomModifier] =
    EmitterBuilder.ofModifier[Boolean] { sink => VDomModifier.delay {
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
    EmitterBuilder.ofModifier[Int] { sink => VDomModifier.delay {

      var lastHeight = 0.0
      var lastScrollTop = 0.0
      var numSteps = 0

      VDomModifier(
        overflow.auto,
        shouldLoad.map {
          case true => VDomModifier(
            div(
              div(
                Styles.flex, alignItems.center, flexDirection.column, justifyContent.center, Styles.growFull,
                button(cls := "ui tiny button", "Load more", marginBottom := "5px", onClick.foreach {
                  numSteps += 1
                  sink.onNext(numSteps)
                  ()
                }),
                WoostLogoComponents.woostLoadingAnimationWithFadeIn
              ),
              onIntersectionWithViewport.foreach { isIntersecting =>
                if (isIntersecting) {
                  numSteps += 1
                  sink.onNext(numSteps)
                }
              }
            ),
            onDomMount.asHtml.foreach { elem =>
              lastHeight = elem.scrollHeight
              lastScrollTop = elem.scrollTop
            },
            onDomPreUpdate.asHtml.foreach { elem =>
              lastScrollTop = elem.scrollTop
            },
            onDomUpdate.asHtml --> inNextAnimationFrame[dom.html.Element] { elem =>
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

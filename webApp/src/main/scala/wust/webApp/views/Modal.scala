package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.facades.fomanticui.ModalOptions
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ModalConfig, Ownable}
import wust.sdk.NodeColor

import scala.scalajs.js


object Modal {
  import wust.graph.Node

  @inline def defaultHeader(node: Node, modalHeader: VDomModifier, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = defaultHeader( Some(node), modalHeader, icon)
  @inline def defaultHeader(modalHeader: VDomModifier, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = defaultHeader( None, modalHeader, icon)
  def defaultHeader(node: Option[Node], modalHeader: VDomModifier, icon: VDomModifier)(implicit ctx: Ctx.Owner): VDomModifier = {
    VDomModifier(
      backgroundColor :=? node.map(node => NodeColor.pageBg.of(node)),
      color.white,
      div(
        Styles.flex,
        flexDirection.row,
        justifyContent.spaceBetween,
        alignItems.center,
        div(
          Styles.flex,
          flexDirection.column,
          lineHeight := "normal",
          node.map { node =>
            div(
              renderAsOneLineText(node)(fontSize := "24px", fontWeight.normal, marginRight := "15px"),
              paddingBottom := "5px",
            )
          },
          div(modalHeader, fontWeight.normal),
        ),
        div(
          Styles.flex,
          Styles.flexStatic,
          icon,
          fontSize.xxLarge,
        ),
      ),
    )
  }
  def modal(config: SourceStream[Ownable[ModalConfig]], globalClose: SinkSourceHandler.Simple[Unit]): VNode = div(
    cls := "ui modal",
    config.map[VDomModifier] { configRx =>
      configRx.flatMap(config => Ownable { implicit ctx =>
        VDomModifier(
          key := scala.util.Random.nextInt, // force new elem on every render. fixes slowly rendering modal in firefox
          config.modalModifier,

          emitter(globalClose.take(1)).useLatestEmitter(onDomMount.asJquery).foreach { e =>
            e.modal("hide")
            // TODO: remove this node from the dom whenever it is hidden (make this thing an observable[option[ownable[modalconfig]]]
            // workaround: kill the ctx owner, so we stop updating this node when it is closed.
            ctx.contextualRx.kill()
          },
          managedElement.asJquery { e =>
            e
              .modal(new ModalOptions {
                onHide = { () =>
                  globalClose.onNext(())
                  config.onHide()
                }: js.Function0[Boolean]
              })
              .modal("show")
            cancelable(() => e.modal("destroy"))
          },

          i(cls := "close icon"),
          div(
            cls := "header modal-header",
            config.header
          ),
          div(
            cls := "content modal-content",
            config.contentModifier,
            div(
              cls := "ui medium modal-inner-content",
              div(
                cls := "description modal-description",
                config.description
              ),
            ),
            config.actions.map { actions =>
              div(
                marginLeft := "auto",
                cls := "actions",
                actions
              )
            }
          )
        )
      })
    }
  )

}


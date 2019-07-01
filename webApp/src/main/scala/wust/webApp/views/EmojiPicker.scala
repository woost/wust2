package wust.webApp.views

import fontAwesome.freeSolid
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.css.{ Styles, ZIndex }
import wust.sdk.Colors
import wust.webApp.WoostNotification
import wust.webApp.state.{ GlobalState, ScreenSize }
import wust.webApp.views.Components._
import wust.facades.wdtEmojiBundle._
import wust.webUtil.Elements.{onGlobalEscape, onGlobalClick}
import monix.reactive.Observer

object EmojiPicker {
  def apply() = {
    div(
      onGlobalEscape.foreach {
        wdtEmojiBundle.close()
      },
      // onGlobalClick.foreach {
      //   wdtEmojiBundle.close()
      // },
      // onClick.stopPropagation --> Observer.empty,
      cls := "wdt-emoji-popup",
      div(
        cls := "wdt-emoji-menu-content",
        div(
          id := "wdt-emoji-menu-header",
          // a(cls := "wdt-emoji-tab active", data.`group-name` := "Recent"),
          a(cls := "wdt-emoji-tab active", data.`group-name` := "People"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Nature"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Foods"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Activity"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Places"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Objects"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Symbols"),
          a(cls := "wdt-emoji-tab", data.`group-name` := "Flags"),
          // a(cls := "wdt-emoji-tab", data.`group-name` := "Custom"),

          div(float.right, cursor.pointer, onClick.stopPropagation.foreach(wdtEmojiBundle.close()), " × ")
        ),
        div(
          cls := "wdt-emoji-scroll-wrapper",
          div(
            id := "wdt-emoji-menu-items",
            input(id := "wdt-emoji-search", tpe := "text", placeholder := "Search"),
            h3(id := "wdt-emoji-search-result-title", "Search Results"),
            div (cls := "wdt-emoji-sections"),
            div(id := "wdt-emoji-no-result", "No emoji found")
          )
        ),
        div(
          id := "wdt-emoji-footer",
          div(
            id := "wdt-emoji-preview",
            span(id := "wdt-emoji-preview-img"),
            div(
              id := "wdt-emoji-preview-text",
              span(id := "wdt-emoji-preview-name"), br(
                span(id := "wdt-emoji-preview-aliases"),
              )
            ),
            div(
              id := "wdt-emoji-preview-bundle",
              span(" ")
            )
          )
        )
      )
    )
  }
}

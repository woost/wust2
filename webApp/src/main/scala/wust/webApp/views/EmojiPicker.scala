package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import wust.facades.wdtEmojiBundle._
import wust.webUtil.Elements.onGlobalEscape

object EmojiPicker {
  def apply() = {
    div(
      onGlobalEscape.foreach {
        wdtEmojiBundle.close()
      },
      onMouseDown.stopPropagation.discard,
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

          div(" × ", float.right, cursor.pointer, onClick.stopPropagation.foreach(wdtEmojiBundle.close()), padding := "4px 10px")
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

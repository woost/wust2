package wust.css

import scalacss.DevDefaults._
import scalacss.internal.ValueT.{Len, TypedAttrBase, TypedAttrT1, ZeroLit}
import scalacss.internal.{Attr, CanIUse, Transform}
import wust.sdk.Colors

import scala.concurrent.duration._

object userDrag extends TypedAttrT1[Len] with ZeroLit {
  import CanIUse.Agent._
  import CanIUse.Support._

  val CanUseDrag: CanIUse.Subject = Map(
    AndroidBrowser -> Set(FullX),
    AndroidChrome -> Set(FullX),
    AndroidFirefox -> Set(FullX),
    AndroidUC -> Set(FullX),
    BlackberryBrowser -> Set(FullX),
    Chrome -> Set(FullX),
    Edge -> Set(FullX),
    Firefox -> Set(FullX),
    IE -> Set(FullX),
    IEMobile -> Set(FullX),
    IOSSafari -> Set(FullX),
    Opera -> Set(FullX),
    OperaMini -> Set(FullX),
    OperaMobile -> Set(FullX),
    Safari -> Set(FullX),
    Samsung -> Set(FullX)
  )

  /// FIXME: this should add -webkit-user-drag and -khtml-user-drag
  override val attr = Attr.real("user-drag", Transform keys CanUseDrag)
  def element = av("element")
}

object gridGap extends TypedAttrT1[Len] with ZeroLit {
  override val attr = Attr.real("grid-gap")
}

object overflowBehavior extends TypedAttrBase {
  override val attr = Attr.real("overflow-behavior")
  def auto = av("auto")
  def contain = av("contain")
  def none = av("none")
}

object Styles extends StyleSheet.Inline {
  import dsl._

  /** width & height 100% */
  val growFull = style(
    width(100 %%),
    height(100 %%)
  )

  val flex = style(
    /* fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686) */
    minWidth(0 px),
    /* fixes full page scrolling when messages are too long */
    minHeight(0 px),
    display.flex,
  )

  val inlineFlex = style(
    /* fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686) */
    minWidth(0 px),
    /* fixes full page scrolling when messages are too long */
    minHeight(0 px),
    display.inlineFlex,
  )

  val flexStatic = style(
    flexGrow(0),
    flexShrink(0)
  )

  val wordWrap = style(
    overflowWrap := "break-word",
    minWidth(0 px),
  )

  val cropEllipsis = style(
    /* BOTH of the following are required for text-overflow */
    whiteSpace.nowrap,
    overflow.hidden,
    textOverflow := "ellipsis",
  )

  val gridOpts = style(
    display.grid,
    gridGap(0 px),
    gridTemplateColumns := "repeat(1, 1fr)",
    gridAutoRows := "minmax(50px, 1fr)"
  )

  val dragFeedBackKf = keyframes(
    (0 %%) -> style(boxShadow := "0px 0px 0px 0px rgba(0,0,0,1)"),
    (100 %%) -> style(boxShadow := "0px 0px 0px 20px rgba(0,0,0,0)")
  )

  val loadingAnimationDashOffsetKf = keyframes(
    (100 %%) -> style(svgStrokeDashOffset := "100")
  )

  val loadingAnimationDashArrayKf = keyframes(
    (0 %%) -> style(svgStrokeDashArray := "30 3.33333"),
    (100 %%) -> style(svgStrokeDashArray := "16.11111 17.22222")
  )

  val fadeInKf = keyframes(
    (0 %%) -> style(opacity(0)),
    (100 %%) -> style(opacity(1))
  )

  val fadeInKfWithVisibility = keyframes(
    (0 %%) -> style(opacity(0), visibility.visible),
    (100 %%) -> style(opacity(1), visibility.visible)
  )

  val errorAnimationKf = keyframes(
    (0 %%) -> style(transform := "translateX(-25%)"),
    (100 %%) -> style(transform := "translateX(25%)")
  )
}

//TODO: port over to Style as inline and reference class via Styles
object CommonStyles extends StyleSheet.Standalone {
  import dsl._

  ".ui.message.warning" - (
    backgroundColor(c"#f79c2b"),
    boxShadow := "none",
    color.white,
    &(".header") - (
      color.white,
    )
  )

  ".ui.message.info" - (
    backgroundColor(c"#44b3f3"),
    boxShadow := "none",
    color.white,
    &(".header") - (
      color.white,
    )
  )

  ".ui.message.error" - (
    backgroundColor(c"#e15666"),
    boxShadow := "none",
    color.white,
    &(".header") - (
      color.white,
    )
  )

  ".ui.message.success" - (
    backgroundColor(c"#00ad73"),
    boxShadow := "none",
    color.white,
    &(".header") - (
      color.white,
    )
  )

  ".toast-container" - (
    zIndex(ZIndex.toast).important // explicitly overwrite z-index of fomantic-ui toasts
  )

  "*, *:before, *:after" - (
    boxSizing.borderBox
  )

  // give empty paragraphs the default line-height
  // https://stackoverflow.com/a/53193642/793909
  "p:empty:before" - (
    content := "\" \"",
    whiteSpace.pre,
  )

  // firefox and chrome interpret placeholder color differently.
  // https://github.com/necolas/normalize.css/issues/277
  ".ui.inverted.input input::placeholder" - (
    opacity(0.8).important,
    color.white.important,
  )


  ":not(input):not(textarea):not([contenteditable=true])," +
  ":not(input):not(textarea):not([contenteditable=true])::after," +
  ":not(input):not(textarea):not([contenteditable=true])::before" - (
//    backgroundColor.blue.important,
    userSelect :=! none,
  )

  ".ui.table.no-inner-table-borders tr td" - (
    // https://github.com/Semantic-Org/Semantic-UI/issues/1980#issuecomment-259151186
    borderTop(0 px).important,
    borderBottom(0 px).important,
  )


  ".enable-text-selection, .enable-text-selection *" - (
    (userSelect :=! "text").important,
    cursor.auto.important
  )

  ".enable-text-selection.a, .enable-text-selection a" - (
    cursor.pointer.important
  )


  "input, button, textarea, :focus" - (
    outline.none // You should add some other style for :focus to help UX/a11y
  )

  // Prevent the text contents of draggable elements from being selectable.
  "[draggable=true]" - (
    userSelect :=! none,
    // FIXME: support -khtml-user-drag
    userDrag.element
  )

  "html, body" - (
    margin(0 px),
    padding(0 px),
    width(100 %%),
    height(100 %%),
  )

  "body" - (
    fontFamily :=! "Lato, sans-serif",
    overflow.hidden
  )

  ".shadow" - (
    boxShadow := "0px 7px 21px -6px rgba(0,0,0,0.75)"
  )

  ".pagenotfound" - (
    opacity(0),
    animationName(Styles.fadeInKf),
    animationDuration(500 milliseconds),
    animationDelay(1000 milliseconds),
    animationFillMode.forwards,
  )


  ".ui.button.inverted" - (
    // reduce inverted box-shadow
    boxShadow := "0 0 0 1px #fff inset !important",
    border(1 px, solid, transparent),
  )


  ".movable-window" - (
    backgroundColor(Color(Colors.sidebarBg)),
    color(Color(Colors.fgColor)),
    position.absolute,
    borderRadius(4 px),
    boxShadow := "0px 10px 18px -6px rgba(0,0,0,0.75)",

    &(".movable-window-title") - (
      Styles.flex,
      justifyContent.spaceBetween,
      alignItems.center,
      backgroundColor(Color(Colors.sidebarBg)),
      padding(5 px),
      borderTopLeftRadius(3 px),
      borderTopRightRadius(3 px),
  )
  )

  ".pageheader" - (
    color.white,
    padding(0 px, 5 px),
  )

  ".pageheader-channeltitle" - (
    fontSize(20 px),
    minWidth(30 px), // min-width and height help to edit if channel name is empty
    lineHeight(1.4285 em), // semantic ui default line height
    marginBottom(0 px), // remove margin when title is in <p> (rendered my markdown)
    Styles.flex, // for notification count
  )

  ".pageheader-channeltitle.nodecard" - (
    paddingTop(0 px),
    paddingBottom(0 px),
    (boxShadow := "none").important,
  )
  ".pageheader-channeltitle.nodecard.project" - (
    backgroundColor.transparent,
    color.white,
  )
  ".pageheader-channeltitle.nodecard .nodecard-content" - (
    padding(2 px),
  )

  ".avatar" - (
    backgroundColor(c"rgb(255, 255, 255)"),
    borderRadius(3 px),
    padding(2 px),
    Styles.flexStatic,
  )

  ".animated-fadein" - (
    opacity(0),
    animationName(Styles.fadeInKf),
    animationDuration(1.5 seconds),
    animationDelay(100 milliseconds),
    animationFillMode.forwards,
  )

  ".animated-late-fadein" - (
    opacity(0),
    visibility.hidden,
    animationName(Styles.fadeInKfWithVisibility),
    animationDuration(2 seconds),
    animationDelay(15 seconds),
    animationFillMode.forwards,
  )

  ".animated-alternating-fade" - (
    opacity(0),
    animationName(Styles.fadeInKf),
    animationDuration(1.5 seconds),
    animationFillMode.forwards,
    animationIterationCount.infinite,
    animationDirection.alternate,
    animationDelay(2 seconds),
    animationTimingFunction.easeInOut,
  )

  ".woost-loading-animation-logo" - (
    svgStrokeDashOffset := "0",
    animation := s"${Styles.loadingAnimationDashOffsetKf.name.value} 23.217s linear infinite, ${Styles.loadingAnimationDashArrayKf.name.value} 5.3721s ease alternate infinite"
  )

  ".ui.dimmer.modals" - (
    zIndex(ZIndex.uiModal)
  )
  ".ui.modal" - (
    zIndex(ZIndex.uiModal + 1),
  )
  ".ui.popup" - (
    padding(5.px),
    zIndex(ZIndex.tooltip),
  )

  ".ui.modal > .close" - (
    color(c"#fff"), // would be black otherwise by semantic ui on small screens

    // always position close-button inside modal
    right(0 px),
    top(0 px),
  )

  "[data-tooltip]:before,[data-tooltip]:after" - (
    padding(5.px),
    zIndex(ZIndex.tooltip),
  )

//  ".modal-header" - ( )
//  ".modal-content" - ( )
  ".modal-inner-content" - (
//    height(100 %%),
  )
  ".modal-description" - (
//    height(100 %%),
  )


  // fix accordion styles breaking search result template
  ".ui.accordion .ui.search .results .content" - (
    padding(0.px),
  )
  ".ui.accordion .ui.search .results .content .title" - (
    padding(0.px),
    borderTop.none,
  )

  // break word in search results
  ".ui.search .results" - (
    Styles.wordWrap
  )

  // fix "search" button going outside screen area on mobile
  ".modal-header .ui.search .prompt" - (
    media.only.screen.maxWidth(640 px) - (
      flexShrink(1),
      )
  )

  ".create-new-prompt.ui.modal > .header" - (
    color.white,
    backgroundColor(c"#6435C9"),
  )

  ".create-new-prompt.ui.modal > .content" - (
    backgroundColor(Color(Colors.contentBg)),
  )


  ".sidebar" - (
    backgroundColor(Color(Colors.sidebarBg)),
    Styles.flexStatic,
    height(100 %%),
    Styles.flex,
    flexDirection.column,
    justifyContent.flexStart,
    alignItems.stretch,
    alignContent.stretch,
  )

  ".overlay-right-sidebar" - (
    right(0 px),
  )

  ".overlay-left-sidebar" - (
    left(0 px),
  )

  ".expanded-sidebar" - (
    height(100 %%),
    zIndex(ZIndex.overlay),
    boxShadow := "0px 0px 3px 0px rgba(0, 0, 0, 0.32)",
  )

  ".expanded-left-sidebar > .sidebar-open" - (
    maxWidth(202.px),
    width(202.px),
  )

  ".expanded-right-sidebar > .sidebar-open" - (
    maxWidth(500.px),
    width(500.px),
  )

  ".overlay-sidebar" - (
    zIndex(ZIndex.overlay),
    position.absolute,
    top(0 px),
    height(100 %%),
    width(100 %%),
    background := "rgba(0,0,0,0.3)"
  )

  ".overlay-right-sidebar > .sidebar" - (
    marginLeft(70 px)
  )

  ".overlay-left-sidebar > .sidebar" - (
    marginRight(50 px)
  )

  // webkit
  ".tribute-container::-webkit-scrollbar," +
  ".tiny-scrollbar::-webkit-scrollbar" - (
    width(5.px),
    height(5.px)
  )
  // firefox
  ".tribute-container," +
  ".tiny-scrollbar" - (
    Attr.real("scrollbar-width") := "thin"
  )

  ".channels" - (
    padding(0 px, 3 px),
    minWidth(200 px),
    overflowY.auto,
  )

  ".channel-line" - (
    Styles.flex,
    alignItems.center,
    cursor.pointer.important, // overwrites cursor from .draggable
    borderRadius(2 px),
    padding(1 px, 0 px, 1 px, 5 px),
  )

  "a.channel-line" - (
    color(Color(Colors.fgColor))
  )

  ".channel-name"  - (
    paddingLeft(3 px),
    paddingRight(3 px)
  )

  ".channel-name > div > p" - (
    margin(0 px) // avoid default p margin. p usually comes from markdown rendering
  )

  ".channel-name," +
  ".channel-name *" - (
    Styles.wordWrap
  )

  ".channelIcons" - (
    overflowY.auto,
    overflowX.hidden, // needed for firefox
    paddingBottom(5 px), // fix overflow (unnecessary scrollbar) in firefox
    Styles.flex,
    flexDirection.column,
    alignItems.flexStart, // in safari and firefox the scrollbar takes away some with. this alignment controls which part of the icons is shown
  )

  ".channelIcons .channelicon" - (
    backgroundColor.white,
  )

  val channelIconDefaultPadding = 4
  ".channelicon" - (
    Styles.flex,
    justifyContent.center,
    alignItems.center,
    fontSize(16 px),

    padding(channelIconDefaultPadding px),
    Styles.flexStatic,
    margin(0 px),
    cursor.pointer.important, // overwrites cursor from .draggable
  )

  /* must be more specific than .ui.button */
  ".ui.button.newChannelButton-large" - (
    marginRight(0 px),
    marginTop(5 px),
    alignSelf.center,
    Styles.flexStatic
  )

  ".ui.button.newChannelButton-small" - (
    marginRight(0 px),
    marginTop(3 px),
    marginBottom(3 px),
    paddingLeft(12 px),
    paddingRight(12 px),
    alignSelf.center,
    Styles.flexStatic,
  )

  ".viewgridAuto" - (
    Styles.gridOpts,
    margin(0 px),
    padding(0 px),
    media.only.screen.minWidth(992 px) - (
      gridTemplateColumns := "repeat(2, 50%)"
    ),
  )

  ".viewgridRow" - (
    Styles.flex,
    margin(0 px),
    padding(0 px)
  )

  /* TODO: too many columns overlaps the content because it autofits the screen height */
  ".viewgridColumn" - (
    Styles.gridOpts,
    margin(0 px),
    padding(0 px)
  )

  ".graphnode," +
  ".graphnode.nodecard" - (
    Styles.wordWrap,
    // textRendering := "optimizeLegibility",
    position.absolute,
    padding(3 px, 5 px),
    cursor.default,
    minHeight(2 em),
    // minWidth(1.5 em),
    borderRadius(3 px),
  )

  ".graphnode-tag" - (
    fontWeight.bold,
    color(c"#FEFEFE"),
  )

  // -- chatview --

  ".chat-history" - (
    height(100 %%),
  )

  ".chat-group-outer-frame" - (
    minWidth(0 px),
    minHeight(0 px),
    Styles.flex,
  )

  ".chat-group-outer-frame > div:first-child" - ( // contains avatar
    paddingTop(8 px),
  )

  ".chat-thread-messages .chat-group-inner-frame" - (
    paddingTop(5 px)
  )

  ".chat-group-inner-frame" - (
    paddingTop(10 px),
    width(100 %%), // expands selection highlight to the whole line
    minWidth(0 px), // fixes word-wrapping in nested flexbox
  )

  ".chatmsg-header" - (
    fontSize(0.8 em),
    lineHeight(100 %%),
    Styles.flex,
    alignItems.center,

    paddingBottom(3 px),
    paddingLeft(2 px),
    media.only.screen.maxWidth(640 px) - (
      paddingBottom(1 px),
    ),
  )

  ".threadview" - (
    &(".chat-expanded-thread") - (
      &(".chatmsg-header") -(
        paddingLeft(30 px), // to align with the other messages, which are pushed right by the expand-button
      )
    )
  )

  ".chatmsg-author" - (
    fontWeight.bold,
    color(c"#50575f"),
    Styles.flexStatic,
  )

  ".chatmsg-date" - (
    marginLeft(8 px),
    fontSize.smaller,
    color.grey
  )

  ".chat-row" - (
    alignItems.center,
    padding(2 px, 20 px, 2 px, 0 px),

    &(".nodecard") - (
      padding(3 px), // overwriting default
    )
  )

  ".chat-row .nodeselection-checkbox.checkbox" - (
    visibility.hidden
  )

  val chatmsgIndent = marginLeft(3 px)
  ".chat-row > .nodecard" - (
    chatmsgIndent,
  )

  ".chatmsg-controls" - (
    visibility.hidden,
    Styles.flex,
    alignItems.center,
    paddingLeft(3 px),
  )

  ".chatmsg-controls > *" - (
    padding(3 px, 5 px)
  )


  ".chat-replybutton" - (
    color(c"rgba(0,0,0,0.5)"),
    cursor.pointer,
  )

  ".chat-replybutton:hover" - (
    color(black)
  )


  //   -- controls on hover --
  // ".chat-row:hover" - (
  //   backgroundColor(c"rgba(255,255,255,0.5)")
  // )

  //TODO: how to generate this combinatorial explosion with scalacss?
  ".chat-row:hover .chatmsg-controls,"+
  ".chat-row:hover .nodeselection-checkbox.checkbox,"+
  ".chat-row:focus .chatmsg-controls,"+
  ".chat-row:focus .nodeselection-checkbox.checkbox" - (
    visibility.visible
  )

  ".chat-thread-messages" - (
    paddingLeft(5 px),
    paddingBottom(5 px),
  )

  ".chat-thread-messages-outer" - (
    marginBottom(5 px),
  )

  ".expand-collapsebutton" - (
    opacity(0.5),
    fontSize(22 px),
  )
  ".expand-collapsebutton:hover" - (
    visibility.visible.important,
    opacity(1),
  )

  val nodeCardShadowOffset = "0px 0.7px 0px 1px"
  val nodeCardShadow = boxShadow := s"$nodeCardShadowOffset rgba(0,0,0,0.12)"
  val nodeCardBackgroundColor = Color(Colors.nodecardBg)
  ".nodecard" - (
    borderRadius(3 px),
    padding(2 px),
    fontWeight.normal,
    overflowX.auto,
  )

  ".nodecard.node" - (
    backgroundColor(nodeCardBackgroundColor),
    color(Color(Colors.fgColor)),
    nodeCardShadow,
  )

  ".nodecard.project" - (
    backgroundColor(nodeCardBackgroundColor),
    color(Color(Colors.fgColor))
  )

  ".right-sidebar-node.nodecard" - (
    boxShadow := "none", // less clutter in right sidebar
    paddingLeft(10 px),
  )

  ".nodecard.project.node-deleted" - (
  )

  ".nodecard.node-deleted" - (
    fontSize.smaller,
    opacity(0.5),
  )

  ".nodecard > .checkbox" - (
    marginTop(3 px),
    marginLeft(3 px),
    marginRight(3 px),
  )

  ".nodecard a" - (
    cursor.pointer
  )

  ".nodecard-content" - (
    Styles.wordWrap,
    padding(2 px),
    minHeight(1 em).important, // height when card is empty. important, because it may be overwritten by Styles.flex which sets minHeight to 0.
  )

  ".note" - (
    &(".markdown") - (
      Styles.wordWrap,
    )  
  )

  ".notifications-view" - (
    &(".notifications-header") - (
      // marginTop(40 px),
      Styles.flex,
      justifyContent.spaceBetween,
      flexWrap.wrapReverse,
      alignItems.center,

      &(".breadcrumbs") - (
        margin(5 px, 0 px),

        &(".divider") - (
          color(c"rgba(165, 165, 165, 0.78)")
        ),
      ),
      &(".breadcrumb") - (
        nodeCardShadow
      ),
      &(".breadcrumb.project") - (
        boxShadow := none
      )
    ),

    &("table") - (
      &("td") - (
        verticalAlign.top,
        &(".notifications-header") - (
          flexWrap.nowrap,
        ),
      ),

      &("td > .nodecard") - (
          display.inlineFlex, // avoid 100% width given by div
      ),
    )
  )


  // -- breadcrumb --
  ".breadcrumbs" - (
    padding(2 px, 2 px), // some padding is needed to display the box-shadow

    Styles.flex,
    alignItems.center,
    overflow.hidden,

    &(".cycle-indicator") - (
      verticalAlign.middle,
      margin(1.px),
      width(0.8.em)
    ),

    &(".divider") - (
      marginLeft(3 px),
      marginRight(3 px),
      color(c"rgba(255, 255, 255, 0.78)"),
      fontSize(18 px),
    ),

    &(".nodecard") - (
      padding(1 px, 3 px),
      &(".nodecard-content") - (
        padding(0 px, 2 px)
      )
    )
  )

  ".pageheader" - (
    &(".breadcrumb") - (
      // pageheader has a colored background. No shadow needed.
      (boxShadow := s"none").important, // overwrite nodecard shadow
      maxWidth(10 em),
    ),

    &(".breadcrumb.nodecard.project") - (
      paddingLeft(0.5.em),
    )
  )

  ".sidebar" - (
    &(".breadcrumbs") - (
    &(".breadcrumb") - (
        maxWidth(7 em),
    ),
      &(".divider") - (
        color(c"rgba(165, 165, 165, 0.78)")
      ),
    ),
  )

  ".breadcrumb" - (
    minWidth(2 em).important, // to leave at least the icon when shrinking, important to overwrite min-width:0 of Styles-flex
  )

  ".breadcrumb," +
  ".breadcrumb *:not(.emoji-outer):not(.emoji-sizer):not(.emoji-inner)" - (
    fontSize(13 px),
  )

  // first/last breadcrumb should not have any margin.
  // this way e.g. the cycle shape is closer to the cycle
  ".breadcrumb:first-of-type" - (
    marginLeft(0 px),
    )
  ".breadcrumb:last-of-type" - (
    marginRight(0 px),
    )


  ".unread-label" - (
    // important to overwrite "ui label"
    float.right,
    marginLeft(5 px).important,
    marginRight(5 px).important,

    color.white.important,
    (fontSize := "x-small").important,
    backgroundColor(Color(Colors.unread)).important,
  )

  ".unread-dot" - (
    float.right,
    marginLeft(5 px),
    marginRight(5 px),

    color(Color(Colors.unread)),
    transition := "color 10s",
    transitionDelay(5 seconds),
  )


  "textarea.inputrow" - (
    marginBottom(0 px).important // since introducing the emoji-picker, textarea got a margin-bottm. Don't know why...
  )

  ".wdt-emoji-picker" - (
    // position emoji button at the top right of input
    bottom.unset,
    top(14 px),
    right(9 px),
  )

  ".wdt-emoji-popup" - (
    fontSize(20 px),
    zIndex(ZIndex.uiSidebarContent)
  )

  ".tribute-container" - (
    boxShadow := "0px 0px 3px 0px rgba(0, 0, 0, 0.32)",
    borderRadius(3.px),
    zIndex(ZIndex.uiSidebarContent).important
  )

  ".tribute-container li" - (
    padding(2.px, 5.px).important
  )

  ".tribute-container li" - (
    padding(2.px, 5.px).important
  )


  val tagMarginPx = 2
  val tagMargin = tagMarginPx.px
  val listViewLeftMargin = 4.px
  val taskPaddingPx = 8
  val taskPadding = taskPaddingPx.px
  val taskPaddingCompactPx = 4
  val taskPaddingCompact = taskPaddingCompactPx.px

  ".kanbancolumnchildren > .nodecard > .nodecard-content" - (
    padding(taskPadding),
  )

  ".tasklist" - (
    paddingTop(1 px), // space for nodecard shadow
    minHeight(20 px).important, // enough vertical space to drop tasks, important overwrites Styles.flex minheight

    Styles.flex,
    flexDirection.column, // make task margin work correctly


    &(".nodecard") - (
      margin(2 px, listViewLeftMargin),

      &(".nodecard-content") - (
        padding(taskPadding, taskPadding, (taskPaddingPx - tagMarginPx).px, taskPadding),// we substract tagMargin to achieve a consistent height of node-cards with and without tags in the same line
      ),

      &(".nodecard-content > .markdown") - (
        marginBottom(tagMargin), // to achieve a consistent height of node-cards with and without tags
      )
    ),

    &(".nodecard > .checkbox") - (
      marginTop((taskPaddingPx + 1) px),
      marginLeft((taskPaddingPx + 1) px),
    ),
  )

  ".tasklist.compact" - (
    &(".nodecard") - (
      &(".nodecard-content") - (
        padding(taskPaddingCompact, taskPaddingCompact, (taskPaddingCompactPx - tagMarginPx).px, taskPaddingCompact),// we substract tagMargin to achieve a consistent height of node-cards with and without tags
      ),
    ),

    &(".nodecard > .checkbox") - (
      marginTop((taskPaddingCompactPx + 1) px),
      marginLeft((taskPaddingCompactPx + 1) px),
    ),
  )

  ".tasklist-header" - (
    fontSize(15 px),
    marginBottom(0 px),
    marginLeft(listViewLeftMargin),
  )

  ".nodecard-content pre" - (
    whiteSpace.preWrap
  )

  ".nodecard-content a" - (
    cursor.pointer.important
  )

  ".markdown ul, .markdown ol" - (
    margin(0 px, 0 px, 1 em), // like <p> from semantic-ui
    paddingLeft(2 em),
  )

  val codeBgColor = c"hsla(210, 58%, 25%, 0.06)"
  ".markdown code .hljs," + // code which is syntax-highlighted
  ".markdown code:not([class])" - (// code which is not syntax-highlighted
    backgroundColor(codeBgColor),
    borderRadius(3 px),
  )

  ".markdown code:not([class])" - ( 
    margin(0 px),
    padding(0.2 em, 0.4 em),
  )

  ".oneline.markdown *:not(.emoji-outer):not(.emoji-sizer):not(.emoji-inner)" - (
    Styles.cropEllipsis,
    fontSize.inherit, // overwrite fontSizes set by e.g. markdown headlines
    lineHeight.inherit,
  )

  ".property" - (
    color.gray,
    fontSize.small,
    padding(0 px, 3 px),
    marginRight(3 px),
    display.inlineBlock,

    &(".property-value") - (
      color(Color(Colors.fgColor)),
    )
  )

  ".tags" - (
    padding( 0 px, 3 px, 0 px, 5 px ),
    Styles.flex,
    flexWrap.wrap,
    minWidth.auto, // when wrapping, prevents container to get smaller than the smallest element
    alignItems.center
  )

  val tagBorderRadius = 2.px
  ".tag" - (
    fontWeight.bold,
    fontSize.small,
    borderRadius(tagBorderRadius),
    padding(0 px, 3 px),
    marginRight(tagMargin),
    marginBottom(tagMargin),
    display.inlineBlock,
  )

  ".tag.colorful" - (
    color(c"#FEFEFE"),
  )

  ".tag.colorful a" - (
    color(c"#FEFEFE"),
    textDecoration := "underline"
  )

  ".tag .markdown" - (
    display.block, // required for textOverflow
    overflow.hidden, // required for textOverflow
  )

  val kanbanColumnPaddingPx = 7
  val kanbanColumnPadding = (kanbanColumnPaddingPx px)
  val kanbanRowSpacing = (8 px)
  val kanbanPageSpacing = (10 px)
  val kanbanCardWidthPx = 250
  val kanbanCardWidth = (kanbanCardWidthPx px)
  val kanbanColumnWidth = ((kanbanColumnPaddingPx + kanbanCardWidthPx + kanbanColumnPaddingPx) px)
  val kanbanColumnBorderRadius = (5 px)

  ".kanbanview" - (
    padding(kanbanPageSpacing),
    height(100 %%),
  )

  ".kanbancolumnarea" - (
    height(100 %%),
  )

  ".kanbannewcolumnarea" - (
    minWidth(kanbanColumnWidth),
    maxWidth(kanbanColumnWidth), // prevents inner fluid textarea to exceed size
  )

  ".kanban-uncategorized-title" - (
    color(c"rgba(0, 0, 0, 0.62)"),
  )

  ".kanbannewcolumnarea, " +
  ".kanbancolumn," + // when dragging sub-column to top-level area
  ".kanbantoplevelcolumn" - (
    marginTop(0 px),
    marginLeft(0 px),
    marginRight(10 px),
  )

  ".kanbantoplevelcolumn" - (
    display.flex,
    minHeight(0 px),
    minWidth(kanbanColumnWidth).important, // conflicts with minwidth of nodecard
    // we don't specify a max-width here. This would cause cards in nested columns to be too big for the available width.
    flexDirection.column,
    maxHeight(100 %%)
  )

  ".kanbansubcolumn" - (
    border(1 px, solid, c"#d0d0d0"),
  )

  ".kanbancolumntitle" - (
    width(100 %%),
    maxWidth(kanbanCardWidth),
    fontSize.large,
    Styles.wordWrap,
    minHeight(1.5 em),
    letterSpacing(0.5 px), // more aesthetic
  )


  ".nodecard .buttonbar.autohide" - (
    backgroundColor(nodeCardBackgroundColor),
    visibility.hidden
  )

  ".nodecard .buttonbar.autohide > div" - (
    color(c"rgb(157, 157, 157)"),
    padding(0 px, 4 px)
  )

  ".nodecard .buttonbar > div" - ( // touch
    color(c"rgb(157, 157, 157)"),
    padding(0 px, 5 px) // more spacing for big fingers
  )

  ".nodecard .buttonbar > div:hover" - (
    backgroundColor(c"rgba(215, 215, 215, 0.9)"),
    color(c"rgb(71, 71, 71)")
  )


  // Childstats
  ".nodecard .childstats" - (
    color.gray,
  )

  ".nodecard .childstat:hover" - (
    color(c"rgb(71, 71, 71)")
  )


  ".kanbancolumnheader .buttonbar.autohide," +
  ".kanban-uncategorized-title .buttonbar.autohide" - (
    visibility.hidden,
  )

  ".kanbancolumnheader .buttonbar," +
  ".kanban-uncategorized-title .buttonbar" - (
    padding(kanbanColumnPadding),
    fontSize.medium // same as in kanban card
  )

  ".nodecard:hover > .buttonbar," +
  ".kanbancolumnheader:hover .buttonbar" - (
    visibility.visible
  )
  ".kanban-uncategorized-title:hover .buttonbar.autohide" - (
    visibility.visible
  )

  ".kanbancolumnheader .buttonbar > div," +
  ".kanban-uncategorized-title .buttonbar > div," +
  ".nodecard .buttonbar > div" - (
    borderRadius(3 px),
    marginLeft(2 px)
  )

  ".kanbancolumnheader .buttonbar.autohide > div," +
  ".kanban-uncategorized-title .buttonbar.autohide > div" - (
    padding(2 px),
    backgroundColor(c"hsla(0, 0%, 34%, 0.72)"),
    color(c"rgba(255, 255, 255, 0.83)")
  )

  ".kanbancolumnheader .buttonbar > div," +
  ".kanban-uncategorized-title .buttonbar > div" - (
    padding(5 px),
    backgroundColor(c"hsla(0, 0%, 34%, 0.72)"),
    color(c"rgba(255, 255, 255, 0.83)"),
  )

  ".kanbancolumnheader .buttonbar > div:hover," +
  ".kanban-uncategorized-title .buttonbar > div:hover" - (
    backgroundColor(c"hsla(0, 0%, 0%, 0.72)"),
    color(white),
  )

  ".kanbancolumnchildren > .nodecard," +
  ".kanbancolumncollapsed > .nodecard" - (
    width(kanbanCardWidth),
  )

  ".kanbancolumn" - (
    backgroundColor(c"#f5f5f5"),
    fontWeight.bold,
    borderRadius(kanbanColumnBorderRadius),
    Styles.flexStatic,
  )

  ".kanbancolumnheader" - (
    Styles.flexStatic,
    Styles.flex,
    alignItems.flexEnd,
    justifyContent.spaceBetween,
  )

  ".kanbancolumnfooter" - (
    Styles.flexStatic
  )

  ".kanbancolumnchildren" - (
    minHeight(50 px), // enough vertical area to drag cards in
    minWidth(kanbanColumnWidth), // enough horizontal area to not flicker width when adding cards
    cursor.default,
    overflowY.auto,
    overflowX.hidden, // needed for firefox
    //or: overflow.initial
    paddingBottom(5 px) // prevents column shadow from being cut off by scrolling
  )

  // we want the sortable container to consume the full width of the column.
  // So that dragging a card/subcolumn in from the side directly hovers the sortable area inside
  // the column, instead of sorting the top-level-columns.
  // therefore, instead setting a padding on the column, we set a margin/padding on the inner elements.
  ".kanbancolumn > .kanbancolumnheader" - (
    padding(kanbanColumnPadding),
  )

  ".kanbancolumnchildren > .nodecard," +
  ".kanbancolumnchildren > .kanbantoplevelcolumn," + // when dragging top-level column into column
  ".kanbancolumnchildren > .kanbancolumn" - (
    marginTop(1 px), // space for nodecard-shadow
    marginRight(kanbanColumnPadding),
    marginLeft(kanbanColumnPadding),
    marginBottom(kanbanColumnPadding)
  )
  ".kanbancolumn .kanbanaddnodefield" - (
    padding(kanbanRowSpacing, kanbanColumnPadding, kanbanColumnPadding, kanbanColumnPadding),
    overflowBehavior.contain
  )

  ".kanbanaddnodefieldtext" - (
    color(white),
    opacity(0.5),
    fontSize.medium,
    fontWeight.normal,
    cursor.pointer,
  )

  ".kanbanaddnodefieldtext:hover" - (
    opacity(1),
  )






  ".nodecard.project .actionbutton" - (
    marginLeft(0 px),
    color(c"#909090")
  )

  ".actionbutton" - (
    cursor.pointer,
    padding(0 px, 5 px),
    borderRadius(50 %%)
  )

  ".actionbutton:hover" - (
    backgroundColor(c"rgba(255,255,255,0.5)")
  )




  val selectedNodesBgColor = c"#85D5FF"
  val selectedNodesBgColorCSS = c"#85D5FF".value
  ".selectednodes" - (
    backgroundColor(selectedNodesBgColor),
    paddingRight(5 px),
    zIndex(ZIndex.overlayLow),
  )

  ".selectednodes > .nodelist" - (
    padding(2 px, 2 px, 0 px, 5 px),
    flexGrow(1),

    borderRadius(5 px),
  )

  ".selectednodes .nodecard" - (
    marginLeft(3 px),
    marginBottom(3 px)
  )

  ".selectednodes .actionbutton" - (
    padding(5 px),
    margin(5 px)
  )


  ".singleButtonWithBg" - (
    padding(1 px, 2 px),
    borderRadius(3 px),
    color(c"rgba(255, 255, 255, 0.83)"),
    backgroundColor(c"hsla(0, 0%, 34%, 0.72)"),
  )

  ".singleButtonWithBg:hover" - (
    color.white,
    backgroundColor(c"hsla(0, 0%, 0%, 0.72)"),
  )

  ".activeButton.singleButtonWithBg:hover" - (
    color(selectedNodesBgColor),
    backgroundColor(c"hsla(0, 0%, 0%, 0.72)"),
  )

  ".tagWithCheckbox .singleButtonWithBg" - (
    visibility.hidden,
  )

  ".tagWithCheckbox:hover .singleButtonWithBg" - (
    visibility.visible,
  )



  // prevents white rectangle on card placeholder
  ".nodecard.draggable--over .buttonbar" - (
    backgroundColor(transparent),
  )

  ".kanbancolumn.draggable--over .buttonbar" - (
    visibility.hidden.important // hide buttons when dragging over column
  )

  ".selectednodes .nodelist.draggable-mirror" - (
    backgroundColor(selectedNodesBgColor),
  )

  ".draggable, .draghandle" - (
    cursor.move,
  )

  ".draggable" - (
    outline.none, // hides focus outline
//    border(2 px, solid, green)
  )

  ".draggable-mirror.drag-feedback" - (
    animationName(Styles.dragFeedBackKf),
    animationDuration(500 milliseconds)
  )

  // -- draggable node
  ".draggable-container .node.draggable--over," +
  ".graphnode.draggable--over," +
  ".chat-expanded-thread.draggable--over," + // chatview
  ".chat-expanded-thread.draggable--over .chat-common-parents > div > div," + // chatview
  ".chat-history.draggable--over," +
  ".chat-row.draggable--over .nodecard" - (
    backgroundColor(Color(Colors.dragHighlight)).important,
    color.white.important,
    opacity(1).important,
    cursor.move.important
  )

  ".chat-expanded-thread.draggable--over .chat-common-parents > div > div" - (// chatview
    borderLeft(3 px, solid, transparent).important,
    opacity(1),
  )

  ".chat-expanded-thread.draggable--over .chat-common-parents > div > div > div" - (// chatview
    opacity(1).important,
  )

  ".chat-row.draggable--over .nodecard *," +
  ".chat-expanded-thread.draggable--over .chat-common-parents .chatmsg-author," + // chatview
  ".chat-expanded-thread.draggable--over .chat-common-parents .chatmsg-date" - ( // chatview
    color.white.important,
  )

  // when dragging over, hide stuff that would normally appear on hover
  ".nodecard.draggable--over .buttonbar," +
  ".draggable-container .nodecard.draggable--over .childstats *," +
  ".chat-expanded-thread.draggable--over .chatmsg-controls," +
  ".chat-expanded-thread.draggable--over .nodeselection-checkbox.checkbox," +
  ".chat-history.draggable--over .chatmsg-controls," +
  ".chat-history.draggable--over .nodeselection-checkbox.checkbox" - (
    visibility.hidden.important,
  )

  ".draggable-mirror" - (
    opacity(0.8).important,
    zIndex(ZIndex.dragging).important, // needs to overlap everything else
  )


  ".chat-row .nodecard.draggable-mirror" - (
    backgroundColor(nodeCardBackgroundColor).important,
    nodeCardShadow.important,
    color.inherit.important
  )

  // -- sortable
  ".sortable-container .draggable-source--is-dragging," +
  ".sortable-container .draggable-source--is-dragging.draggable--over" - (
    backgroundColor(c"rgba(102, 102, 102, 0.71)").important,
    color(transparent).important,
    borderColor(transparent).important,
  )

  ".sortable-container .draggable-source--is-dragging *" - (
    visibility.hidden
  )

  // -- draggable actionbutton
  ".node.draggable--over .actionbutton" - (
    backgroundColor.inherit.important,
    cursor.move.important
  )

  ".text" - (
    cursor.text
  )

  ".topbar" - (
    paddingRight(5 px),
    Styles.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.center,
  )

  ".topBannerContainer" - (
    Styles.flex,
    Styles.flexStatic,
    flexDirection.column,
  )

  ".topBanner" - (
    Styles.flex,
    Styles.flexStatic,
    alignItems.center,
    justifyContent.center,
    cursor.pointer,
    fontSize.larger,
    fontWeight.bold,
    width(100 %%),
    height(40 px),
    color.white,
    backgroundColor(c"#494653"),
  )

  val tabsPadding = 7.px
  ".viewswitcher-item" - (
    fontSize(20 px),
    height(100 %%),
    padding(tabsPadding),
    borderRadius(2 px, 2 px, 0 px, 0 px),
    marginLeft(3 px),
    Styles.flex,
    alignItems.center,
    cursor.pointer,
    color(Color(Colors.fgColor)),
  )
  ".viewswitcher-item.active" - (
    backgroundColor(Color(Colors.contentBg)),
  )

  ".viewswitcher-item.inactive" - (
    backgroundColor(rgba(0,0,0, 0.1)),
    color(rgba(255,255,255,0.75)),
  )

  ".viewswitcher-item .ui.dropdown" - (
    fontSize(inherit) // overwrite semantic ui default font-size
  )

  // error page background animation
  ".error-animation-bg" - (
    animation := s"${Styles.errorAnimationKf.name.value} 3s ease-in-out infinite alternate",
    backgroundImage := "linear-gradient(-60deg, #6c3 50%, #09f 50%)",
    bottom(0 px),
    left(-50 %%),
    opacity(0.5),
    position.fixed,
    right(-50 %%),
    top(0 px),
    zIndex(-1),
  )

  ".error-animation-bg2" - (
    animationDirection.alternateReverse,
    animationDuration(4 seconds),
  )

  ".error-animation-bg3" - (
    animationDuration(5 seconds),
  )

  ".error-animation-content" - (
    minWidth(300 px),
    backgroundColor(c"rgba(255,255,255,.8)"),
    borderRadius(0.25 em),
    boxShadow := "0 0 .25em rgba(0,0,0,.25)",
    left(50%%),
    padding(10 vmin),
    position.fixed,
    top(50%%),
    transform := "translate(-50%, -50%)",
  )
}

object StyleRendering {
  def renderAll: String = CommonStyles.renderA[String] ++ Styles.renderA[String]
}

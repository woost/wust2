package wust.css

import scalacss.DevDefaults._
import scalacss.internal.{Attr, CanIUse, Literal, Transform}
import scalacss.internal.ValueT.{Len, TypedAttrBase, TypedAttrT1, ZeroLit}
import wust.css.Styles.Woost

import scala.concurrent.duration._

object ZIndex {
  val controls = 10
  val draggable = 100
  val overlaySwitch = 300
  val selected = 400
  val overlayLow = 1000
  val tooltip = 1500
  val loading = 1750
  val overlay = 12000
  val formOverlay = 13000
  val uiModal = 15000
  val uiSidebar = 16000
  val dragging = 20000
}

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

  val slim = style(
    margin(0 px),
    padding(0 px)
  )

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

  object Woost {
    val color = c"#6636b7"
  }

}

//TODO: port over to Style as inline and reference class via Styles
object CommonStyles extends StyleSheet.Standalone {
  import dsl._

  "*, *:before, *:after" - (
    boxSizing.borderBox
  )

  ":not(input):not(textarea):not([contenteditable=true])," +
  ":not(input):not(textarea):not([contenteditable=true])::after," +
  ":not(input):not(textarea):not([contenteditable=true])::before" - (
//    backgroundColor.blue.important,
    userSelect := none,
  )


  ".enable-text-selection, .enable-text-selection *" - (
    (userSelect := "text").important,
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
    userSelect := none,
    // FIXME: support -khtml-user-drag
    userDrag.element
  )

  "html, body" - (
    Styles.slim,
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

  ".mainview" - (
    flexDirection.column,
    Styles.growFull,
    Styles.flex
  )

  ".taglist" - (
    width(180.px),
    paddingLeft(10.px),
    paddingRight(10.px),
    paddingBottom(10.px),
  )
  ".pagenotfound" - (
    opacity(0),
    animationName(Styles.fadeInKf),
    animationDuration(500 milliseconds),
    animationDelay(1000 milliseconds),
    animationFillMode.forwards,
  )

  // -- breadcrumb --
  ".breadcrumbs" - (
    padding(2 px, 3 px),

    Styles.flex,
    alignItems.flexStart,
    // flexWrap.wrap,
    overflowX.auto,
    Styles.flexStatic,

    &(".cycle-indicator") - (
      verticalAlign.middle,
      margin(1.px),
      width(0.8.em)
    )
  )

  ".breadcrumbs .divider" - (
    marginLeft(3 px),
    marginRight(3 px),
    color(c"#666"),
    fontSize(16 px),
    fontWeight.bold
  )

  ".breadcrumb" - (
    margin(0 px).important,
    height(1.5 em).important,
  )

  ".breadcrumb," +
  ".breadcrumb *" - (
    maxWidth(10 em),
    overflow.hidden,
    fontSize(13 px).important,
    fontWeight.normal,
  )

  ".breadcrumb.nodecard," +
  ".breadcrumb .nodecard-content" - (
    minHeight(0 em),
    border.none,
  )

  ".breadcrumb .markdown" - (
    paddingTop(0 px),
    paddingBottom(0 px),
  )

  ".breadcrumb .markdown *" - (
    /* BOTH of the following are required for text-overflow */
    whiteSpace.nowrap,
    overflow.hidden,
    textOverflow := "ellipsis",
  )

  // first/last breadcrumb should not have any margin.
  // this way e.g. the cycle shape is closer to the cycle
  ".breadcrumb:first-of-type" - (
    marginLeft(0 px),
    )
  ".breadcrumb:last-of-type" - (
    marginRight(0 px),
    )


  val sidebarBgColorCSS = c"#2A3238"
  val sidebarBgColor = sidebarBgColorCSS.value

  val tabsOutlineColor = c"#6B808F"
  val tabsInactiveBackgroundColor = c"#97aaba"
  val tabsOutlineWidth = 2.px

  ".moveable-window" - (
    position.absolute,
    zIndex(ZIndex.overlayLow),
    border(2.px, solid,  CommonStyles.sidebarBgColorCSS),
    borderRadius(4.px, 4.px, 4.px, 4.px),
    boxShadow := "5px 10px 18px -6px rgba(0,0,0,0.75)"
  )

  ".pageheader" -(
    borderBottom(tabsOutlineWidth, solid, tabsOutlineColor),
  )

  ".pageheader-channeltitle" - (
    fontSize(20 px),
    Styles.wordWrap,
    marginBottom(0 px), // remove margin when title is in <p> (rendered my markdown)
    minWidth(30 px), // min-width and height help to edit if channel name is empty
    minHeight(1 em),
    cursor.text,
  )

  ".pageheader-channeltitle .nodecard-content" - (
    minHeight(0 em),
    paddingBottom(3 px),
  )

  ".pageheader-channeltitle.nodecard > .checkbox" - (
    marginTop(9 px),
  )

  ".avatar" - (
    backgroundColor(c"rgba(255, 255, 255, 0.90)"),
    borderRadius(2 px),
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

//  ".modal-header" - ( )
//  ".modal-content" - ( )
  ".modal-inner-content" - (
//    height(100 %%),
  )
  ".modal-description" - (
//    height(100 %%),
  )

  ".sidebar" - (
    color.white,
    background := sidebarBgColorCSS,
    borderRight(2 px, solid, sidebarBgColorCSS),
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
    boxShadow := "-2px 4px 5px 0px #6b5959",
  )

  ".expanded-left-sidebar > .sidebar-open" - (
    maxWidth(202.px),
    width(202.px),
  )

  ".expanded-right-sidebar > .sidebar-open" - (
    maxWidth(500.px),
    width(500.px),
  )

  ".expanded-left-sidebar > .sidebar-close" - (
  )

  ".expanded-right-sidebar > .sidebar-close" - (
  )

  ".expanded-right-sidebar .accordion" - (
    //overflowY.scroll,
    display.flex,
    flexDirection.column,
    height(100 %%)
  )

  ".expanded-right-sidebar .accordion .content" - (
    overflowY.hidden,
    flex := "0 1 auto"
  )
  // upper content box
  ".expanded-right-sidebar .accordion .content:nth-child(2)" - (
    display.flex, // allows inner element to grow, which then displays a scrollbar
  )
  // lower content box
  ".expanded-right-sidebar .accordion .content:nth-child(4)" - (
    flexGrow(1),
    flexBasis := "50%"
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
    marginLeft(100 px)
  )

  ".overlay-left-sidebar > .sidebar" - (
    marginRight(50 px)
  )

  ".customChannelIcon" - (
    Styles.flex,
    alignItems.center,
    justifyContent.center,
    color(c"#333"),
    backgroundColor(c"#e2e2e2"),
  )

  ".channels .customChannelIcon" - (
    width(30 px),
    height(30 px),
  )

  ".channelIcons .customChannelIcon" - (
    width(40 px),
    height(40 px),
  )

  ".channels" - (
    paddingLeft(3 px),
    minWidth(200 px),
    overflowY.auto,
    color(c"#C4C4CA"),
  )

  ".channel-line" - (
    Styles.flex,
    alignItems.center,
    cursor.pointer.important, // overwrites cursor from .draggable
    marginBottom(2 px),
    borderRadius(2 px),
  )

  ".channel-line > .channelicon" - (
    marginRight(5 px),
    borderRadius(2 px),
  )

  ".channel-line-hover-show" - (
    visibility.hidden
  )

  ".channel-line:hover .channel-line-hover-show" - (
    visibility.visible
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

  val channelIconDefaultPadding = 4
  ".channelicon" - (
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
    paddingLeft(12 px),
    paddingRight(12 px),
    alignSelf.center,
    Styles.flexStatic,
  )

  ".viewgridAuto" - (
    Styles.slim,
    Styles.gridOpts,
    media.only.screen.minWidth(992 px) - (
      gridTemplateColumns := "repeat(2, 50%)"
    )
  )

  ".viewgridRow" - (
    Styles.slim,
    Styles.flex
  )

  /* TODO: too many columns overlaps the content because it autofits the screen height */
  ".viewgridColumn" - (
    Styles.slim,
    Styles.gridOpts,
  )

  /* inspired by https://github.com/markdowncss/air/blob/master/index.css */
  ".article" - (
    color(c"#444"),
    fontFamily :=! "'Open Sans', Helvetica, sans-serif",
    fontWeight :=! "300",
    margin(6 rem, auto, 1 rem),
    maxWidth(100 ch)
  )

  ".article p" - (
    color(c"#777")
  )

  ".article h1" - (
    paddingBottom(0.3 em),
    borderBottom(1 px, solid, c"#eaecef")
  )

  ".article.focuslink" - (
    float.left,
    marginLeft(2 em),
    width(2 em),
    textAlign.right,
    paddingRight(10 px),
    display.inlineBlock,
    color(c"#BBB"),
    fontWeight.normal,
    cursor.pointer
  )

  ".article.focuslink > *" - (
    visibility.hidden
  )

  ".graphnode" - (
    Styles.wordWrap,
    textRendering := "optimizeLegibility",
    position.absolute,
    padding(3 px, 5 px),
    cursor.default,
  )

  ".graphnode.nodecard" - (
    borderRadius(6 px),
  )

  ".graphnode-tag" - (
    fontWeight.bold,
    color(c"#FEFEFE"),
    borderRadius(3 px),
    border(1 px, solid, transparent), // to have the same size as nodecard
  )

  // FIXME: also generate -moz-selection?
  ".splitpost".selection - (
    color(red),
    background := "blue", // why no background(...) possible?
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

  ".chatmsg-author" - (
    fontWeight.bold,
    color(c"#50575f")
  )

  ".chatmsg-date" - (
    marginLeft(8 px),
    fontSize.smaller,
    color.grey
  )

  ".chat-row" - (
    alignItems.center,
    padding(2 px, 20 px, 2 px, 0 px)
  )

  ".chat-row .nodeselection-checkbox.checkbox" - (
    visibility.hidden
  )

  val chatmsgIndent = marginLeft(3 px)
  ".chat-row > .tag" - (
    chatmsgIndent, // when a tag is displayed at message position
    whiteSpace.normal, // displaying tags as content should behave like normal nodes
  )

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

  val nodeCardShadow = boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)"
  val nodeCardBackgroundColor = c"#FEFEFE"
  ".nodecard" - (
    borderRadius(3 px),
    backgroundColor(nodeCardBackgroundColor),
    color(c"#212121"), // same as rgba(0, 0, 0, 0.87) from semantic ui
    fontWeight.normal,
    overflowX.auto,

    border(1 px, solid, transparent), // when dragging this will be replaced with a color
    nodeCardShadow,
  )

  ".nodecard > .checkbox" - (
    marginTop(5 px),
    marginLeft(5 px),
    marginRight(3 px),
  )

  ".nodecard a" - (
    cursor.pointer
  )

  ".nodecard-content > *" - (
    padding(2 px, 4 px), // when editing, clicking on the padding does not unfocus
  )

  ".nodecard-content" - (
    Styles.wordWrap,
    /* display.inlineBlock, */
    border(1 px, solid, transparent), /* placeholder for the dashed border when dragging */
    minHeight(2 em), // height when card is empty
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

  ".markdown code .hljs" - ( // code which is syntax-highlighted
    borderRadius(3 px),
  )

  ".markdown code:not([class])" - ( // code which is not syntax-highlighted
    // like github
    backgroundColor(c"rgba(27, 31, 35, 0.05)"),
    borderRadius(3 px),
    // fontSize(85 %%),
    margin(0 px),
    padding(0.2 em, 0.4 em),
  )

  ".nodecard.node-deleted" - (
    fontSize.smaller,
    opacity(0.5),
  )
  ".nodecard.node-deleted .nodecard-content" - (
    textDecoration := "line-through",
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
    color(c"#FEFEFE"),
    borderRadius(tagBorderRadius),
    padding(0 px, 3 px),
    marginRight(2 px),
    marginTop(1 px),
    marginBottom(1 px),
    whiteSpace.nowrap,
    display.inlineBlock,
    overflow.hidden, // required for textOverflow
    maxWidth(100 %%), // required for textOverflow
  )
  
  ".tag .markdown *" - (
    /* BOTH of the following are required for text-overflow */
    whiteSpace.nowrap,
    overflow.hidden,
    textOverflow := "ellipsis",
  )

  ".tag a" - (
    color(c"#FEFEFE"),
    textDecoration := "underline"
  )

  ".tag .markdown" - (
    display.block, // required for textOverflow
    overflow.hidden, // required for textOverflow
  )

  ".tagdot" - (
    width(1 em),
    height(1 em),
    borderRadius(50%%),
    border(1 px, solid, transparent), // when dragging this will be replaced with a color
    padding(0 px, 3 px),
    marginRight(2 px),
    marginTop(1 px),
    marginBottom(1 px),
    cursor.pointer,
    display.inlineBlock
  )


  val kanbanColumnPaddingPx = 7
  val kanbanColumnPadding = (kanbanColumnPaddingPx px)
  val kanbanRowSpacing = (8 px)
  val kanbanPageSpacing = (10 px)
  val kanbanCardWidthPx = 250
  val kanbanCardWidth = (kanbanCardWidthPx px)
  val kanbanColumnWidth = ((kanbanColumnPaddingPx + kanbanCardWidthPx + kanbanColumnPaddingPx) px)
  val kanbanColumnBorderRadius = (3 px)

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
    minHeight(100 px),
    backgroundColor(c"rgba(158, 158, 158, 0.25)"),
    borderRadius(kanbanColumnBorderRadius),
    cursor.pointer,
  )

  ".kanbannewcolumnareaform" - (
    padding(7 px)
  )

  ".kanban-uncategorized-title" - (
    padding(5 px),
    color(c"rgba(0, 0, 0, 0.62)"),
  )

  ".kanbannewcolumnareacontent" - (
    width(kanbanColumnWidth),
    paddingTop(35 px),
    textAlign.center,
    fontSize.larger,
    color(c"rgba(0, 0, 0, 0.62)"),
  )

  ".kanbannewcolumnarea .kanbancolumn" - (
    margin(0 px).important
  )

  ".kanbannewcolumnarea, " +
  ".kanbancolumn," + // when dragging sub-column to top-level area
  ".kanbantoplevelcolumn" - (
    marginTop(0 px),
    marginLeft(0 px),
    marginRight(10 px),
  )

  ".kanbantoplevelcolumn" - (
    border(1 px, solid, white),
    display.flex,
    minHeight(0 px),
    minWidth(kanbanColumnWidth).important, // conflicts with minwidth of nodecard
    // we don't specify a max-width here. This would cause cards in nested columns to be too big for the available width.
    flexDirection.column,
    maxHeight(100 %%)
  )

  ".kanbansubcolumn" - (
    border(1 px, solid, white)
  )

  ".kanbancolumntitle" - (
    width(100 %%),
    maxWidth(kanbanCardWidth),
    fontSize.large,
    Styles.wordWrap,
    minHeight(1.5 em),
    letterSpacing(0.5 px), // more aesthetic
  )


  ".nodecard .buttonbar" - (
    padding(2 px, 4 px),
    visibility.hidden
  )

  ".nodecard .buttonbar > div" - (
    color(c"rgb(157, 157, 157)"),
    padding(2 px)
  )

  ".nodecard .buttonbar > div:hover" - (
    backgroundColor(c"rgba(215, 215, 215, 0.9)"),
    color(c"rgb(71, 71, 71)")
  )


  // Childstats
  ".nodecard .childstats" - (
    color.gray,
  )
  ".kanbancolumnfooter .childstats" - (
      fontWeight.normal,
      color(c"rgba(255, 255, 255, 0.81)"),
    )

  ".nodecard .childstat:hover" - (
    color(c"rgb(71, 71, 71)")
  )
  ".kanbancolumnfooter .childstats:hover" - (
    color.white
  )



  ".kanbancolumnheader .buttonbar" - (
    padding(kanbanColumnPadding),
    visibility.hidden,
    fontSize.medium // same as in kanban card
  )
  ".kanban-uncategorized-title .buttonbar" - (
    padding(kanbanColumnPadding),
    visibility.hidden,
    fontSize.medium // same as in kanban card
  )

  ".kanbancolumnheader > p" - (
    marginBottom(0 em) // default was 1 em
  )

  ".nodecard:hover > .buttonbar," +
  ".kanbancolumnheader:hover .buttonbar" - (
    visibility.visible
  )
  ".kanban-uncategorized-title:hover .buttonbar" - (
    visibility.visible
    )

  ".kanbancolumnheader .buttonbar > div," +
  ".nodecard .buttonbar > div" - (
    borderRadius(3 px),
    marginLeft(2 px)
  )

  ".kanbancolumnheader .buttonbar > div" - (
    padding(2 px),
    backgroundColor(c"hsla(0, 0%, 34%, 0.72)"),
    color(c"rgba(255, 255, 255, 0.83)")
  )

  ".kanbancolumnheader .buttonbar > div:hover" - (
    backgroundColor(c"hsla(0, 0%, 0%, 0.72)"),
    color(white)
  )

  ".kanbancolumnchildren > .nodecard," +
  ".kanbancolumncollapsed > .nodecard" - (
    width(kanbanCardWidth),
  )

  ".kanbancolumn" - (
    color(c"#FEFEFE"),
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
    marginTop(0 px),
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








  ".actionbutton" - (
    cursor.pointer,
    padding(0 px, 5 px),
    marginLeft(2 px),
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
    border(3 px, solid, transparent) // will be replaced when dragging
  )

  ".selectednodes .nodecard" - (
    marginLeft(3 px),
    marginBottom(3 px)
  )

  ".selectednodes .actionbutton" - (
    padding(5 px),
    margin(5 px)
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

  ".dropzone" - (
    backgroundColor(c"rgba(184,65,255,0.5)")
  )

  // -- draggable node
  ".node.draggable--over," +
  ".graphnode.draggable--over," +
  ".chat-expanded-thread.draggable--over," + // chatview
  ".chat-expanded-thread.draggable--over .chat-common-parents > div > div," + // chatview
  ".chat-history.draggable--over," +
  ".chat-row.draggable--over .nodecard" - (
    backgroundColor(c"rgba(55, 66, 74, 1)").important,
    color.white.important,
    opacity(1).important,
    cursor.move.important
  )

  ".sidebar .draggable--over" - (
    color(c"rgba(55, 66, 74, 1)").important,
    backgroundColor.white.important,
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
  ".nodecard.draggable--over .childstats *," +
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


  // -- draggable nodecard
  ".nodecard.draggable--over," +
  ".chat-row.draggable--over .nodecard" - (
    borderTop(1 px, solid, transparent).important,
    (boxShadow := "0px 1px 0px 1px rgba(93, 120, 158,0.45)").important
  )

  ".chat-row .nodecard.draggable-mirror" - (
    backgroundColor(nodeCardBackgroundColor).important,
    nodeCardShadow.important,
    color.inherit.important
  )

  // -- draggable chanelicon
  ".channelicon.draggable-mirror" - (
    border(2 px, solid, c"#383838").important
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

  val topBarHeight = 45
  ".topbar" - (
    paddingRight(5 px),
    height(topBarHeight px),
    color.white,
    background := sidebarBgColorCSS,
    Styles.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.center,
  )

  ".topBannerContainer" - (
    width(100 %%),
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
//    backgroundColor(Woost.color),
    // backgroundColor(c"#ff0266"), // pink
    backgroundColor.seagreen,
    borderBottom(1 px, solid, black)
  )

  val tabsPadding = 5.px
  ".viewswitcher-item" - (
    fontSize.larger,
    height(100 %%),
    padding(tabsPadding),
    borderRadius(3 px, 3 px, 0 px, 0 px),
    marginLeft(2 px),
    border(2 px, solid, tabsOutlineColor),
    marginBottom(-tabsOutlineWidth),
    Styles.flex,
    alignItems.center,
    cursor.pointer,
  )
  ".viewswitcher-item.active" - (
    // bgColor set programatically to topic color
  )
  ".viewswitcher-item.active:hover" - (
    zIndex(1500)
  )
  ".viewswitcher-item.inactive" - (
    backgroundColor :=! s"${tabsInactiveBackgroundColor.value + "55"}",
    border :=! s"2px solid ${ tabsOutlineColor.value + "55"} ",
    borderBottomColor(CommonStyles.tabsOutlineColor),
    color(rgba(0,0,0,0.7)),
  )
  ".viewswitcher-item.inactive span" - (opacity(0.5))
  ".viewswitcher-item.inactive .fa-fw" - (opacity(0.5))
  val tabsBoxShadowColor = c"#000000"
  ".viewswitcher-item.single.active" - (
    boxShadow := s"1px -1px 2px -1px ${tabsBoxShadowColor.value}"
  )
  ".viewswitcher-item.double.right" - (
    marginLeft(0 px),
    borderLeft(0 px),
  )
  ".viewswitcher-item.double.left.active" - (
    boxShadow := s"2px -1px 1px -1px ${tabsBoxShadowColor.value}",
    // -- we reduce the border & increase padding, to keep the tab in place --
    borderRight(0 px),
    paddingRight((tabsPadding.n + 2).px),
  )
  ".viewswitcher-item.double.right.active" - (
    boxShadow := s"1px -1px 1px -1px ${tabsBoxShadowColor.value}"
  )

  ".emoji-outer" - (
    width(1 em),
    height(1 em),
    display.inlineBlock,
  )
  ".emoji-inner" - (
    display.inlineBlock,
    width(100 %%),
    height(100 %%),
    verticalAlign.bottom,
  )
  ".emoji-sizer" - (
    fontSize.larger,
  )


  ".main-viewrender" - (
    // FIXME: overlapped by tabs
    boxShadow := "0px -1px 5px 0px"
  )
}

object StyleRendering {
  def renderAll: String = CommonStyles.renderA[String] ++ Styles.renderA[String]
}

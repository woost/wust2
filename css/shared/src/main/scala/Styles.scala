package wust.css

import scalacss.DevDefaults._
import scalacss.internal.{Attr, CanIUse, Literal, Transform}
import scalacss.internal.ValueT.{Len, TypedAttrBase, TypedAttrT1, ZeroLit}
import scala.concurrent.duration._

// TODO: generate by sbt:
// https://stackoverflow.com/questions/23409993/defining-sbt-task-that-invokes-method-from-project-code

object ZIndex {
  val controls = 10
  val draggable = 100
  val overlayLow = 1000
  val overlay = 2000
  val dragging = 10000
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
    (0 %%) -> style(boxShadow := "0px 0px 0px 0px rgba(133,213,255,1)"),
    (100 %%) -> style(boxShadow := "0px 0px 0px 20px rgba(133,213,255,0)")
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
    cursor.text.important
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
    fontFamily :=! "'Roboto Slab', serif",
    overflow.hidden
  )

  ".shadow" - (
    boxShadow := "0px 7px 21px -6px rgba(0,0,0,0.75)"
  )

  ".mainview" - (
    flexDirection.column,
    Styles.growFull
  )

  ".pagenotfound" - (
    opacity(0),
    animationName(Styles.fadeInKf),
    animationDuration(1.5 seconds),
    animationDelay(2000 milliseconds),
    animationFillMode.forwards,
  )

  // -- breadcrumb --
  ".breadcrumbs" - (
    padding(1 px, 3 px),
    alignItems.center,
    overflowX.auto,
    fontSize(12 px),
    Styles.flexStatic,

    &(".cycle-indicator") - (
      verticalAlign.middle,
      margin(1.px),
      width(0.8.em)
    )
  )

  ".breadcrumb .tag" - (
    marginLeft(1 px),
    marginRight(2 px)
  )

  // first/last breadcrumb should not have any margin.
  // this way e.g. the cycle shape is closer to the cycle
  ".breadcrumb .tag:first-of-type" - (
    marginLeft(0 px),
    )
  ".breadcrumb .tag:last-of-type" - (
    marginRight(0 px),
    )

  ".breadcrumbs .divider" - (
    marginLeft(3 px),
    marginRight(3 px),
    color(c"#666"),
    fontSize.larger,
    fontWeight.bold
  )

  ".pageheader-channeltitle" - (
    fontSize(20 px),
    Styles.wordWrap,
    marginBottom(0 px), // remove margin when title is in <p> (rendered my markdown)
    minWidth(30 px), // min-width and height help to edit if channel name is empty
    minHeight(1 em),
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
    animation := s"${Styles.fadeInKf.name.value} 1s, ${Styles.loadingAnimationDashOffsetKf.name.value} 23.217s linear infinite, ${Styles.loadingAnimationDashArrayKf.name.value} 5.3721s ease alternate infinite"
  )

  ".sidebar" - (
    color.white,
    transition := "background-color 0.5s",
    Styles.flexStatic,
    height(100 %%),
    Styles.flex,
    flexDirection.column,
    justifyContent.flexStart,
    alignItems.stretch,
    alignContent.stretch,
  )

  ".overlay-sidebar" - (
    zIndex(ZIndex.overlay),
    position.absolute,
    left(0 px),
    top(0 px),
    height(100 %%),
    width(100 %%),
    background := "rgba(0,0,0,0.3)"
  )

  ".overlay-sidebar > .sidebar" - (
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
  )

  ".channel-line > .channelicon" - (
    marginRight(5 px),
    borderRadius(2 px),
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
    overflowY.auto
  )

  ".channelicon" - (
    border(2 px, solid, transparent),
    padding(2 px),
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
    /* border-radius : 3px; */ /* affects graph rendering performance */
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

  ".chat-history .chatmsg-header" - (
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

  ".chat-history .chatmsg-author" - (
    fontWeight.bold,
    color(c"#50575f")
  )

  ".chat-history .chatmsg-date" - (
    marginLeft(8 px),
    fontSize.smaller,
    color.grey
  )

  ".chat-row" - (
    alignItems.center,
    padding(2 px, 20 px, 2 px, 0 px)
  )

  ".chat-row .checkbox" - (
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

  ".workflow .chatmsg-controls > *, .chat-history .chatmsg-controls > *" - (
    padding(3 px, 5 px)
  )


  ".chat-history .chat-replybutton" - (
    color(c"rgba(0,0,0,0.5)"),
    cursor.pointer,
  )

  ".chat-history .chat-replybutton:hover" - (
    color(black)
  )


  //   -- controls on hover --
  ".chat-row:hover" - (
    backgroundColor(c"rgba(255,255,255,0.5)")
  )

  //TODO: how to generate this combinatorial explosion with scalacss?
  ".chat-row:hover .chatmsg-controls,"+
  ".chat-row:hover .checkbox,"+
  ".chat-row:hover .transitivetag,"+
  ".chat-row:focus .chatmsg-controls,"+
  ".chat-row:focus .checkbox,"+
  ".chat-row:focus .transitivetag" - (
    visibility.visible
  )

  ".chat-thread-messages" - (
    marginLeft(5 px),
    paddingLeft(5 px),
    paddingBottom(5 px),
    marginBottom(5 px),
  )

  ".workflow-wrapper" - (
    height(100 %%),
    width(100 %%),
    overflow.auto
  )

  ".workflow .chatmsg-line" - (
    display.inlineFlex
  )

  ".workflow .chatmsg-line .node-deleted" - (
    opacity(0.5),
    textDecoration := "line-through",
  )

  val selectedNodesBgColor = c"#85D5FF"
  ".workflow .activeEditableControls" - (
    margin(2 px),
    backgroundColor(selectedNodesBgColor),
    )

  ".workflow .activeEditableControls li" - (
    listStyle := "none",
    display.inline,
    margin(3 px),
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
    minWidth(6 em).important,
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

  ".nodecard-content > p," +
  ".nodecard-content > p > div > div > p" - ( 
    margin(0 px) // avoid default p margin. p usually comes from markdown rendering
  )

  ".nodecard.node-deleted" - (
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

  ".transitivetag" - (
    visibility.hidden
  )


  val tagBorderRadius = 2.px
  ".tag" - (
    fontWeight.bold,
    fontSize.small,
    color(c"#FEFEFE"),
    borderRadius(tagBorderRadius),
    border(1 px, solid, transparent), // when dragging this will be replaced with a color
    padding(0 px, 3 px),
    marginRight(2 px),
    marginTop(1 px),
    marginBottom(1 px),
    whiteSpace.nowrap,
    cursor.pointer,
    display.inlineBlock
  )

  ".tag a" - (
    color(c"#FEFEFE"),
    textDecoration := "underline"
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

  ".kanbannewcolumnarea.draggable-container--over" - (
    backgroundColor(transparent),
  )

  ".kanbannewcolumnareacontent" - (
    width(kanbanColumnWidth),
    paddingTop(35 px),
    textAlign.center,
    fontSize.larger,
    color(c"rgba(0, 0, 0, 0.62)"),
  )

  ".kanbannewcolumnarea > .nodecard" - ( // this constellation appears, when dragging card over NewColumnArea to create a new column
    width(kanbanColumnWidth),
    height(100 px),
    margin(0 px).important,
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

  ".kanbancolumnarea > .nodecard" - (
    minWidth(kanbanColumnWidth).important, // conflicts with minwidth of nodecard
    marginTop(0 px),
    marginLeft(0 px),
    marginRight(10 px),
  )


  ".kanbansubcolumn" - (
    border(1 px, solid, white)
  )

  ".kanbancolumntitle" - (
    width(100 %%),
    maxWidth(kanbanCardWidth),
    // TODO: separate style for word-breaking in nodes
    Styles.wordWrap,
    minHeight(2 em), // if title is empty
    letterSpacing(0.5 px), // more aesthetic
  )


  ".nodecard .buttonbar" - (
    backgroundColor(nodeCardBackgroundColor),
    padding(2 px, 4 px),
    visibility.hidden
  )

  ".nodecard.draggable--over .buttonbar" - (
    backgroundColor(transparent),
    )

  ".nodecard .buttonbar > div" - (
    color(c"rgb(157, 157, 157)"),
    padding(2 px)
  )

  ".nodecard .buttonbar > div:hover" - (
    backgroundColor(c"rgba(215, 215, 215, 0.9)"),
    color(c"rgb(71, 71, 71)")
  )


  ".kanbancolumnheader .buttonbar" - (
    padding(kanbanColumnPadding),
    visibility.hidden,
    fontSize.medium // same as in kanban card
  )

  ".kanbancolumnheader > p" - (
    marginBottom(0 em) // default was 1 em
  )

  ".kanbancolumn.draggable--over .buttonbar" - (
    visibility.hidden.important // hide buttons when dragging over column
  )

  ".nodecard:hover .buttonbar," +
  ".kanbancolumnheader:hover .buttonbar" - (
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
  ".kanbanisolatednodes > .nodecard" - (
    width(kanbanCardWidth),
    borderRadius(3 px),
    fontSize.medium,
  )

  ".kanbancolumn" - (
    color(c"#FEFEFE"),
    fontWeight.bold,
    fontSize.large,
    borderRadius(kanbanColumnBorderRadius),
    Styles.flexStatic,
  )

  ".kanbancolumnheader" - (
    Styles.flexStatic,
    Styles.flex,
    alignItems.flexEnd,
    justifyContent.spaceBetween,
  )

  ".kanbancolumnchildren" - (
    minHeight(50 px), // enough vertical area to drag cards in
    minWidth(kanbanColumnWidth), // enough horizontal area to not flicker width when adding cards
    cursor.default,
    overflowY.auto,
    paddingBottom(5 px) // prevents column shadow from being cut off by scrolling
  )

  // we want the sortable container to consume the full width of the column.
  // So that dragging a card/subcolumn in from the side directly hovers the sortable area inside
  // the column, instead of sorting the top-level-columns.
  // therefore, instead setting a padding on the column, we set a margin/padding on the inner elements.
  ".kanbancolumn > .kanbancolumnheader" - (
    padding(kanbanColumnPadding, kanbanColumnPadding, 0 px, kanbanColumnPadding),
  )

  ".kanbancolumnheader.kanbancolumncollapsed" - (
    padding(kanbanColumnPadding),
  )

  ".kanbancolumnchildren > .nodecard," +
  ".kanbancolumnchildren > .kanbantoplevelcolumn," + // when dragging top-level column into column
  ".kanbancolumnchildren > .kanbancolumn" - (
    marginTop(kanbanRowSpacing),
    marginRight(kanbanColumnPadding),
    marginLeft(kanbanColumnPadding),
    marginBottom(0 px)
  )
  ".kanbancolumn > .kanbanaddnodefield" - (
    padding(kanbanRowSpacing, kanbanColumnPadding, kanbanColumnPadding, kanbanColumnPadding),
    overflowBehavior.contain
  )

  ".kanbanaddnodefield > div" - (
    color(c"rgba(255,255,255,0.5)"),
    fontSize.medium,
    fontWeight.normal,
    cursor.pointer,
  )

  ".kanbanaddnodefield:hover > div" - (
    color(white)
  )

  ".kanbanisolatednodes" - (
    Styles.flex,
    flexWrap.wrap,
    alignItems.flexStart,
    marginBottom(5 px),
    minHeight(2 em),
    maxHeight(200 px),
    overflowY.auto,
    padding(2 px) // prevents card shadow from being cut off by scrolling
  )

  ".kanbanisolatednodes > .nodecard" - (
    marginRight(5 px),
    marginTop(5 px),
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

  // -- draggable selectednodes
  ".selectednodes .nodelist.draggable--over," +
  ".selectednodes.draggable--over" - (
    backgroundColor(c"rgba(65,184,255, 1)").important,
    )

  ".selectednodes .nodelist.draggable-source--is-dragging" - (
    backgroundColor(selectedNodesBgColor).important,
    border(3 px, dashed, c"#48A4D4")
  )

  ".selectednodes.draggable-mirror .actionbutton" - (
    display.none
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

  ".draggable-mirror.drag-feedback," +
  ".draggable-mirror .drag-feedback" - (
    animationName(Styles.dragFeedBackKf),
    animationDuration(500 milliseconds)
  )

  ".dropzone" - (
    backgroundColor(c"rgba(184,65,255,0.5)")
  )

  // -- draggable node
  ".draggable-container .node.draggable--over," +
  ".chat-expanded-thread.draggable--over > .chat-row:first-child:not(.draggable-source--is-dragging) .nodecard," + // threadview
  ".chat-expanded-thread.draggable--over .chat-common-parents," + // chatview
  ".chat-expanded-thread.draggable--over .chat-common-parents > div > div," + // chatview
  ".chat-history.draggable--over," +
  ".chat-group-inner-frame.draggable--over > div:nth-child(2) > .chat-row:first-child:not(.draggable-source--is-dragging) .nodecard," + // first message in a group
  ".chat-row.draggable--over .nodecard" - (
    backgroundColor(c"rgba(65,184,255, 1)").important,
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
  ".chat-expanded-thread.draggable--over > .chat-row:first-child:not(.draggable-source--is-dragging) .nodecard *," + // threadview
  ".chat-expanded-thread.draggable--over .chat-common-parents .chatmsg-author," + // chatview
  ".chat-expanded-thread.draggable--over .chat-common-parents .chatmsg-date," + // chatview
  ".chat-group-inner-frame.draggable--over > div:nth-child(2) > .chat-row:first-child:not(.draggable-source--is-dragging) .nodecard *" - ( // first message in a group
    color.white.important,
  )

  ".draggable-mirror" - (
    opacity(1).important,
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

  ".chat-row.draggable-mirror .tag," +
  ".chat-row.draggable-mirror .tagdot," +
  ".chat-row.draggable-mirror .checkbox," + // checkbox is also set to visible when checked
  ".chat-row.draggable-mirror .collapsebutton" - (
    visibility.hidden.important
  )

  val onDragNodeCardColor = c"rgba(0,0,0,0.5)"
  ".nodecard.draggable-source--is-dragging," +
  ".nodecard.draggable-source--is-dragging.draggable--over," +
  ".chat-row.draggable-source--is-dragging .nodecard,"+
  ".chat-row.draggable-source--is-dragging.draggable--over .nodecard," +
  ".chat-group-inner-frame.draggable--over > div:nth-child(2) > .chat-row.draggable-source--is-dragging .nodecard" - (
    backgroundColor(white).important,
    color(onDragNodeCardColor).important,
    (boxShadow := none).important,
    border(1 px, dashed, onDragNodeCardColor).important,
  )


  ".chat-row.draggable-source--is-dragging .nodecard *,"+
  ".chat-row.draggable--over.draggable-source--is-dragging .nodecard *," +
  ".chat-group-inner-frame.draggable--over > div:nth-child(2) > .chat-row.draggable-source--is-dragging .nodecard *" - (
    backgroundColor(white).important,
    color(onDragNodeCardColor).important,
  )

  // -- draggable chanelicon
  ".channelicon.draggable-mirror" - (
    border(2 px, solid, c"#383838").important
  )

  ".channelicon.draggable-source--is-dragging" - (
    border(2 px, dashed, c"#383838").important
    )


  // -- draggable tag
  val onDragNodeTagColor = c"rgba(255,255,255,0.8)"
  ".tag.draggable-source--is-dragging," +
  ".tag.draggable-source--is-dragging.draggable--over," +
  ".tagdot.draggable-source--is-dragging," +
  ".tagdot.draggable-source--is-dragging.draggable--over" - (
    border(1 px, dashed, onDragNodeTagColor).important,
    color(onDragNodeTagColor).important,
    backgroundColor(c"#98A3AB").important
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

  // -- draggable actionbutton, transitivetag
  ".node.draggable--over .actionbutton" - (
    backgroundColor.inherit.important,
    cursor.move.important
  )

  ".chat-row.draggable-source--is-dragging .transitivetag" - (
    visibility.visible
    )

  ".text" - (
    cursor.text
  )

  val nonBookmarkShadowOpts = "0px 2px 10px 0px rgba(0,0,0,0.75)"
  ".non-bookmarked-page-frame" - (
    padding(20 px),
    media.only.screen.maxWidth(640 px) - (
      padding(7 px)
    ),
    boxShadow := "inset " + nonBookmarkShadowOpts
  )

  ".non-bookmarked-page-frame > *" - (
    boxShadow := nonBookmarkShadowOpts
  )

  ".topbar" - (
    paddingRight(5 px),
    height(45 px),
    color.white,
    transition := "background-color 0.5s", // fades on page change
    Styles.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.center,
  )

  ".viewbar" - (
    height(100 %%),
  )

  ".viewbar label" - (
    height(100 %%),
    paddingTop(2 px),
    paddingLeft(5 px),
    paddingRight(5 px),
    marginTop(2 px),
    marginBottom(-2 px),
    border(1 px, solid, transparent),
    borderRadius(2 px),
    &("svg") - (
      verticalAlign.middle
    )
  )

  ".viewbar .checked + label" - (
    // backgroundColor(rgb(48, 99, 69)),
    color(c"#111111"),
    border(1 px, solid, white),
    borderTop(2 px, solid, white),
    borderBottom(1 px, solid, transparent)
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
    fontSize.medium,
  )
}

object StyleRendering {
  def renderAll: String = CommonStyles.renderA[String] ++ Styles.renderA[String]
}

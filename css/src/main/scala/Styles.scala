package wust.css

import scalacss.DevDefaults._
import scalacss.internal.{Attr, Literal, Transform, CanIUse}
import scalacss.internal.ValueT.{TypedAttrT1, ZeroLit, Len}

// TODO: generate by sbt:
// https://stackoverflow.com/questions/23409993/defining-sbt-task-that-invokes-method-from-project-code

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
  // def normal = av(L.normal)
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

  val gridOpts = style(
    display.grid,
    gridGap(0 px),
    gridTemplateColumns := "repeat(1, 1fr)",
    gridAutoRows := "minmax(50px, 1fr)"
  )

}

//TODO: port over to Style as inline and reference class via Styles
object CommonStyles extends StyleSheet.Standalone {
  import dsl._

  "*, *:before, *:after" - (
    boxSizing.borderBox
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

  ".hard-shadow" - (
    borderTop(1 px, solid, rgba(158, 158, 158, 0.19)),
    boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)"
  )

  ".mainview" - (
    flexDirection.column,
    Styles.growFull
  )

  // -- breadcrumb --
  ".breadcrumbs" - (
    padding(1 px, 3 px),
    display.flex,
    overflowX.auto,
    fontSize(12 px),
    Styles.flexStatic,
  )

  ".breadcrumb" - ()

  ".breadcrumbs .divider" - (
    marginLeft(1 px),
    marginRight(3 px),
    color(c"#666"),
    fontWeight.bold
  )

  // -- sidebar --
  ".sidebar" - (
    minWidth(40 px),
    maxWidth(250 px),
    color.white,
    transition := "flex-basis 0.2s, background-color 0.5s",
    Styles.flexStatic,
    height(100 %%),
    display.flex,
    flexDirection.column,
    justifyContent.flexStart,
    alignItems.stretch,
    alignContent.stretch,
  )

  ".noChannelIcon" - (
    margin(0 px),
  )

  ".channels .noChannelIcon" - (
    width(30 px),
    height(30 px),
  )

  ".channelIcons .noChannelIcon" - (
    width(40 px),
    height(40 px),
  )

  ".channels" - (
    overflowY.auto,
    color(c"#C4C4CA"),
  )

  ".channel" - (
    paddingRight(3 px),
    display.flex,
    alignItems.center,
    cursor.pointer,
  )

  ".channelIcons" - (
    overflowY.auto
  )

  /* must be more specific than .ui.button */
  ".ui.button.newGroupButton-large" - (
    marginRight(0 px),
    marginTop(5 px),
    alignSelf.center,
    Styles.flexStatic
  )

  ".ui.button.newGroupButton-small" - (
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
    display.flex
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

  /* Prevent the text contents of draggable elements from being selectable. */
  "[draggable]" - (
    userSelect := "none",
    // FIXME: support -khtml-user-drag
    userDrag.element
  )

  ".graphnode" - (
    wordWrap.breakWord,
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
  ".chatmsg-outer-frame" - ()

  ".chatmsg-avatar" - (
    margin(5 px)
  )

  ".chatmsg-author" - (
    fontWeight.bold,
    color(c"#50575f")
  )

  ".chatmsg-deleted" - (
    textDecoration := "line-through",
    cursor.default
  )

  ".chatmsg-inner-frame" - (
    width(100 %%),
    display.block,
    padding(0 px, 0 px, 0 px, 10 px),
    margin(5 px, 0 px)
  )

  ".chatmsg-header" - (
    fontSize(0.8 em),
    lineHeight(100 %%),
    paddingBottom(3 px)
  )

  ".chatmsg-body" - (
    alignItems.center,
    padding(0 px, 20 px, 3 px, 0 px)
  )

  val messageBackground = c"#FEFEFE"

  ".chatmsg-card" - (
    cursor.move, /* TODO: What about cursor when selecting text? */
    borderRadius(3 px),
    marginLeft(3 px),
    backgroundColor(messageBackground),
    overflowX.auto
  )

  ".chatmsg-content" - (
    wordWrap.breakWord,
    wordBreak.breakAll,
    padding(2 px, 4 px),
    /* display.inlineBlock, */
    border(1 px, solid, transparent) /* placeholder for the dashed border when dragging */
  )

  ".chatmsg-content pre" - (
    whiteSpace.normal
  )

  ".chatmsg-controls" - (
    visibility.hidden,
    display.flex,
    alignItems.center,
    paddingLeft(3 px),
    marginLeft.auto
  )

  ".chatmsg-date" - (
    marginLeft(8 px),
    fontSize.smaller,
    color.grey
  )

  "div.tags" - (
    padding(
      0 px,
      3 px,
      0 px,
      5 px
    )
  )

  "span.tag" - (
    fontWeight.bold,
    fontSize.small,
    color(c"#FEFEFE"),
    borderRadius(2 px),
    padding(0 px, 3 px),
    margin(1 px, 3 px, 1 px, 0 px),
    whiteSpace.nowrap,
    cursor.pointer,
    display.inlineBlock
  )

  "span.tag .removebutton" - (
    cursor.pointer,
    padding(0 px, 5 px),
    marginLeft(2 px),
    borderRadius(50 %%)
  )

  "span.tag .removebutton:hover" - (
    backgroundColor(c"rgba(255,255,255,0.5)")
  )

  // -- controls on hover --
  ".chatmsg-body".hover - (
    &(".chatmsg-controls") - (
      visibility.visible
    ),
    backgroundColor(c"rgba(255,255,255,0.5)")
  )

  /* TODO: Focus is only used as a quick hack in order to use controls on mobile browser */
  ".chatmsg-body".focus - (
    &("focus.chatmsg-controls") - (
      visibility.visible
    )
  )

  // -- draggable --
  ".chatmsg-inner-frame .draggable--over" - (
    backgroundColor(c"#aaccff"),
    borderRadius(3 px)
  )

  ".chatmsg-inner-frame .draggable-mirror" - (
    backgroundColor(messageBackground),
    borderRadius(3 px),
    overflow.hidden,
    borderTop(1 px, solid, rgba(158, 158, 158, 0.19)),
    boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)",
  )

  val onDragColor = c"#999"
  ".chatmsg-inner-frame .draggable-source--is-dragging" - (
    color(onDragColor),
    borderRadius(3 px),
    border(1 px, solid, onDragColor),
    borderStyle.dashed
  )

  ".chatmsg-inner-frame .draggable--over.draggable-source--is-dragging" - (
    backgroundColor.inherit
  )

  ".text" - (
    cursor.text
  )

  val nonBookmarkShadowOpts = "0px 2px 10px 0px rgba(0,0,0,0.75)"
  ".non-bookmarked-page-frame" - (
    padding(20 px),
    boxShadow := "inset " + nonBookmarkShadowOpts
  )

  ".non-bookmarked-page-frame > *" - (
    boxShadow := nonBookmarkShadowOpts
  )
}

object StyleRendering {
  def renderAll: String = CommonStyles.renderA[String] ++ Styles.renderA[String]

  //    final def render[Out](implicit r: Renderer[Out], env: Env): Out =
  // cssRegister.render

  // import java.io.{File, PrintWriter}

  // /** Generates css files from scalacss styles */
  // def Build() = {
  //   // val w = new PrintWriter(new File("../webApp/src/css/generated.css"))
  //   val w = new PrintWriter(new File("../webApp/src/css/style.css"))
  //   w.write(renderAll)
  //   w.close()
  // }
}

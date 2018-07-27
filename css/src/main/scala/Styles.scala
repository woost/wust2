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
    color.white,
    transition := "background-color 0.5s",
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
    wordWrap.breakWord,
    wordBreak := "break-word",
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
  ".chatmsg-group-outer-frame" - (
    marginTop(10 px),
    // media.only.screen.minWidth(500 px) - (
      //TODO: how to use Styles.flex ?
      /* fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686) */
      minWidth(0 px),
      /* fixes full page scrolling when messages are too long */
      minHeight(0 px),
      display.flex,
    // )
  )

  ".chatmsg-avatar" - (
    marginRight(10 px),
    width(40 px),
    media.only.screen.maxWidth(500 px) - (
      width(20 px)
    ),
  )

  ".chatmsg-group-inner-frame" - (
    width(100 %%),
    display.block,
    media.only.screen.maxWidth(500 px) - (
      marginLeft(-2 px)
    )
  )

  ".chatmsg-header" - (
    fontSize(0.8 em),
    lineHeight(100 %%),
    paddingBottom(3 px),
    paddingLeft(2 px)
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

  ".chatmsg-line" - (
    cursor.move,
    alignItems.center,
    padding(2 px, 20 px, 2 px, 0 px)
  )

  ".chatmsg-line .checkbox" - (
    visibility.hidden
  )

  val chatmsgIndent = marginLeft(3 px)
  ".chatmsg-line > .tag" - (
    chatmsgIndent, // when a tag is displayed at message position
    whiteSpace.normal, // displaying tags as content should behave like normal nodes
  )

  ".chatmsg-line > .nodecardcompact" - (
    chatmsgIndent,
  )

  ".chatmsg-line > .tag *" - (
    wordWrap.breakWord,
    wordBreak := "break-word",
  )

  ".chatmsg-controls" - (
    media.only.screen.maxWidth(500 px) - (
      display.none
    ),
    visibility.hidden,
    display.flex,
    alignItems.center,
    paddingLeft(3 px),
    marginLeft.auto
  )

  ".chatmsg-controls > *" - (
    padding(3 px, 5 px)
  )


  // -- controls on hover --
  // TODO: Focus is only used as a quick hack in order to use controls on mobile browser
  ".chatmsg-line:hover, .chatmsg-line:focus" - (
    backgroundColor(c"rgba(255,255,255,0.5)")
  )

  //TODO: how to generate this combinatorial explosion with scalacss?
  ".chatmsg-line:hover .chatmsg-controls,"+
  ".chatmsg-line:hover .checkbox,"+
  ".chatmsg-line:hover .transitivetag,"+
  ".chatmsg-line:focus .chatmsg-controls,"+
  ".chatmsg-line:focus .checkbox,"+
  ".chatmsg-line:focus .transitivetag" - (
    visibility.visible
  )


  val messageBackground = c"#FEFEFE"

  val nodeCardCompactShadow = boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)"
  val nodeCardCompactBackgroundColor = backgroundColor(messageBackground)
  ".nodecardcompact" - (
    cursor.move, /* TODO: What about cursor when selecting text? */
    borderRadius(3 px),
    nodeCardCompactBackgroundColor,
    color(c"rgba(0, 0, 0, 0.87)"), // from semantic ui
    fontWeight.normal,
    overflowX.auto,

    border(1 px, solid, transparent), // when dragging this will be replaced with a color
//    borderTop(1 px, solid, rgba(158, 158, 158, 0.19)),
    nodeCardCompactShadow
  )

  ".nodecardcompact a" - (
    cursor.pointer
  )

  ".nodecardcompact-content" - (
    wordWrap.breakWord,
    wordBreak := "break-word",
    padding(2 px, 4 px),
    /* display.inlineBlock, */
    border(1 px, solid, transparent) /* placeholder for the dashed border when dragging */
  )

  ".nodecardcompact-content pre" - (
    whiteSpace.preWrap
  )

  ".node-deleted .nodecardcompact-content" - (
    textDecoration := "line-through",
    cursor.default
  )

  ".tags" - (
    padding( 0 px, 3 px, 0 px, 5 px ),
    display.flex,
    flexWrap.wrap,
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

  ".kanbancolumn" - (
    margin(0 px, 5 px, 20 px, 5 px),
    boxShadow := "0px 1px 0px 1px rgba(158,158,158,0.45)",
  )

  ".kanbansubcolumn" - (
    padding(7 px),
    color(c"#FEFEFE"),
    fontWeight.bold,
    fontSize.large,
    boxShadow := "0px 1px 0px 1px rgba(99,99,99,0.45)",
    cursor.move
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
    backgroundColor(c"#85D5FF"),
    padding(5 px, 5 px, 2 px, 5 px),
    cursor.move
  )

  ".selectednodes .nodecardcompact" - (
    marginLeft(3 px)
  )

  ".draggable" - (
    outline.none // hides focus outline
  )

  ".dropzone" - (
    backgroundColor(c"rgba(184,65,255,0.5)")
  )

  // -- draggable node
  " .node.draggable--over, .chatmsg-line.draggable--over .nodecardcompact" - (
    backgroundColor(c"rgba(65,184,255, 1)").important,
    color.white.important,
    opacity(1).important,
    cursor.move.important
  )

  ".draggable-mirror" - (
    opacity(1).important,
    zIndex(100), // needs to overlap checkboxes, selectednodesbar
  )


  // -- draggable nodecardcompact
  ".nodecardcompact.draggable--over," +
  ".chatmsg-line.draggable--over .nodecardcompact" - (
    borderTop(1 px, solid, transparent).important,
    (boxShadow := "0px 1px 0px 1px rgba(93, 120, 158,0.45)").important
  )

  ".chatmsg-line .nodecardcompact.draggable-mirror" - (
    nodeCardCompactBackgroundColor.important,
    nodeCardCompactShadow.important,
    color.inherit.important
  )

  ".chatmsg-line.draggable-mirror .tag," +
  ".chatmsg-line.draggable-mirror .tagdot" - (
    visibility.hidden
  )

  val onDragNodeCardCompactColor = c"rgba(0,0,0,0.5)"
  ".nodecardcompact.draggable-source--is-dragging," +
  ".chatmsg-line.draggable-source--is-dragging .nodecardcompact,"+
  ".chatmsg-line.draggable--over.draggable-source--is-dragging .nodecardcompact,"+
  ".chatmsg-line.draggable--over .nodecardcompact.draggable-source--is-dragging" - (
    backgroundColor(white).important,
    (boxShadow := none).important,
    border(1 px, dashed, onDragNodeCardCompactColor).important,
    color(onDragNodeCardCompactColor).important,
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

  // -- draggable selectednodes
  ".selectednodes.draggable--over" - (
    backgroundColor(c"rgba(65,184,255, 1)").important,
  )

  ".selectednodes.draggable-mirror > *" - (
    display.none
  )

  ".selectednodes.draggable-mirror > .nodelist" - (
    display.flex,
  )

  ".selectednodes.draggable-mirror" - (
    borderRadius(3 px)
  )

  // -- draggable actionbutton, transitivetag
  ".node.draggable--over .actionbutton" - (
    backgroundColor.inherit.important,
    cursor.move.important
  )

  ".chatmsg-line.draggable-source--is-dragging .transitivetag" - (
    visibility.visible
    )

  ".text" - (
    cursor.text
  )

  val nonBookmarkShadowOpts = "0px 2px 10px 0px rgba(0,0,0,0.75)"
  ".non-bookmarked-page-frame" - (
    padding(20 px),
    media.only.screen.maxWidth(500 px) - (
      padding(7 px)
    ),
    boxShadow := "inset " + nonBookmarkShadowOpts
  )

  ".non-bookmarked-page-frame > *" - (
    boxShadow := nonBookmarkShadowOpts
  )

  ".feedbackhint" - (
    opacity(0.5)
    )

  ".feedbackhint:hover" - (
    opacity(1)
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

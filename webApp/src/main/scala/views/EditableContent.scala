package wust.webApp.views

import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._

import scala.concurrent.duration._
import scala.scalajs.js

object EditableContent {

  private def epochMilliToInputValue(epoch: EpochMilli): String = {
    val date = new js.Date(epoch.toLong)
    date.toISOString.slice(0, 10)
  }

  private val nodeDataToInputFieldMod: PartialFunction[NodeData, VDomModifier] = {
    case data: NodeData.Integer      => VDomModifier(Elements.integerInputMod, value := data.str)
    case data: NodeData.Decimal      => VDomModifier(Elements.decimalInputMod, value := data.str)
    case data: NodeData.Date         => VDomModifier(Elements.dateInputMod, value := epochMilliToInputValue(data.content))
    case data: NodeData.EditableText => VDomModifier(Elements.textInputMod, value := data.str)
  }

  private def updateNodeData(str: String, data: NodeData.Content): Option[NodeData.Content] = data match {
    case data: NodeData.Integer      => StringOps.safeToInt(str).collect { case i if i != data.content => data.copy(content = i) }
    case data: NodeData.Decimal      => StringOps.safeToInt(str).collect { case i if i != data.content => data.copy(content = i) }
    case data: NodeData.Date         => wust.webUtil.StringOps.safeToEpoch(str).collect { case i if i != data.content => data.copy(content = i) }
    case data: NodeData.EditableText => data.updateStr(str)
    case _                           => None
  }

  private val commonEditMods = VDomModifier(
    cls := "enable-text-selection", // fix for macos safari (contenteditable should already be selectable, but safari seems to have troube with interpreting `:not(input):not(textarea):not([contenteditable=true])`)
    whiteSpace.preWrap, // preserve white space in Markdown code
  )

  private def saveElement[T](state: GlobalState, editMode: Var[Boolean], initialRender: Var[VNode], renderFn: T => VNode, stringToElement: String => Option[T], toGraphChanges: T => GraphChanges)(implicit ctx: Ctx.Owner): String => Unit = { text =>
    if(editMode.now && text.nonEmpty) {
      stringToElement(text) match {
        case Some(updatedElement) =>
          Var.set(
            initialRender -> renderFn(updatedElement),
            editMode -> false
          )

          val changes = toGraphChanges(updatedElement)
          state.eventProcessor.changes.onNext(changes)
        case None =>
          editMode() = false
      }
    }
  }


  //TODO wrap in form to check reportvalidity on inputfield constraints!
  private def editTextInputField[T](state: GlobalState, editMode: Var[Boolean], initialRender: Var[VNode], renderFn: T => VNode, stringToElement: String => Option[T], toGraphChanges: T => GraphChanges)(implicit ctx: Ctx.Owner): VNode = {
    val save = saveElement(state, editMode, initialRender, renderFn, stringToElement, toGraphChanges)

    input(
      onDomMount.asHtml --> inNextAnimationFrame { elem => elem.focus() },
      onDomUpdate.asHtml --> inNextAnimationFrame { elem => elem.focus() },

      commonEditMods,

      onBlur.value.transform(_.delayOnNext(200 millis)) foreach { save(_) }, // we delay the blur event, because otherwise in chrome it will trigger Before the onEscape, and we want onEscape to trigger frist.
      BrowserDetect.isMobile.ifFalse[VDomModifier](VDomModifier(
        onEnter.value foreach { save(_) },
        onEscape foreach { editMode() = false }
      )),
    )
  }

  private def editTextModifier[T](state: GlobalState, editMode: Var[Boolean], initialRender: Var[VNode], renderFn: T => VNode, stringToElement: String => Option[T], toGraphChanges: T => GraphChanges)(implicit ctx: Ctx.Owner): VDomModifier = {
    val save = { contentEditable:HTMLElement =>
      val text = contentEditable.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String] // textContent would remove line-breaks in firefox
      saveElement(state, editMode, initialRender, renderFn, stringToElement, toGraphChanges).apply(text)
    }

    VDomModifier(
      onDomMount.asHtml --> inNextAnimationFrame { elem => elem.focus() },
      onDomUpdate.asHtml --> inNextAnimationFrame { elem => elem.focus() },
      contentEditable := true,
      backgroundColor := "#FFF",
      color := "#000",
      cursor.auto,

      commonEditMods,

      onFocus foreach { e => document.execCommand("selectAll", false, null) },
      onBlur.transform(_.delayOnNext(200 millis)) foreach { e => save(e.target.asInstanceOf[HTMLElement]) }, // we delay the blur event, because otherwise in chrome it will trigger Before the onEscape, and we want onEscape to trigger frist.
      BrowserDetect.isMobile.ifFalse[VDomModifier](VDomModifier(
        onEnter foreach { e => save(e.target.asInstanceOf[HTMLElement]) },
        onEscape foreach { editMode() = false }
        //TODO how to revert back if you wrongly edited something on mobile?
      )),
      onClick.stopPropagation foreach {} // prevent e.g. selecting node, but only when editing
    )
  }

  private def editOrRender(editMode: Var[Boolean], initialRender: Rx[VNode], editRender: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {

    p( // has different line-height than div and is used for text by markdown
      outline := "none", // hides contenteditable outline
      keyed, // when updates come in, don't disturb current editing session
      Rx {
        if(editMode()) editRender else initialRender()
      },
    )
  }

  def ofNode(state: GlobalState, node: Node, editMode: Var[Boolean], renderFn: Node => VNode)(implicit ctx: Ctx.Owner): VNode = {
    val initialRender: Var[VNode] = Var(renderFn(node))

    node match {
      case node: Node.Content =>
        val editRender = node.data match {
          case textData: NodeData.EditableText => Some(
            VDomModifier(editTextModifier[Node](state, editMode, initialRender, renderFn, str => textData.updateStr(str).map(data => node.copy(data = data)), GraphChanges.addNode(_)), textData.str)
          )
          case nodeData => nodeDataToInputFieldMod.lift(nodeData).map { inputMod =>
            editTextInputField[Node](state, editMode, initialRender, renderFn, str => updateNodeData(str, nodeData).map(data => node.copy(data = data)), GraphChanges.addNode(_)).apply(inputMod)
          }
        }

        editRender.fold(initialRender.now)(editOrRender(editMode, initialRender, _))

      case user: Node.User if !user.data.isImplicit && user.id == state.user.now.id =>
        val editRender = VDomModifier(editTextModifier[Node](state, editMode, initialRender, renderFn, str => user.data.updateName(str).map(data => user.copy(data = data)), GraphChanges.addNode(_)), user.data.name)
        editOrRender(editMode, initialRender, editRender)

      case _ => initialRender.now
    }
  }

  def textModifier(state: GlobalState, element: String, editMode: Var[Boolean], renderFn: String => VNode, toGraphChanges: String => GraphChanges)(implicit ctx: Ctx.Owner): VNode = {
    val initialRender: Var[VNode] = Var(renderFn(element))

    val editRender = VDomModifier(editTextModifier[String](state, editMode, initialRender, renderFn, Some(_), toGraphChanges), element)
    editOrRender(editMode, initialRender, editRender)
  }

  def textInputField(state: GlobalState, element: String, editMode: Var[Boolean], renderFn: String => VNode, toGraphChanges: String => GraphChanges)(implicit ctx: Ctx.Owner): VNode =
    customInputField[String](state, element, editMode, renderFn, Some(_), toGraphChanges, VDomModifier(Elements.textInputMod, value := element))
  def integerInputField(state: GlobalState, element: Int, editMode: Var[Boolean], renderFn: Int => VNode, toGraphChanges: Int => GraphChanges)(implicit ctx: Ctx.Owner): VNode =
    customInputField[Int](state, element, editMode, renderFn, StringOps.safeToInt(_), toGraphChanges, VDomModifier(Elements.integerInputMod, value := element.toString))
  def doubleInputField(state: GlobalState, element: Double, editMode: Var[Boolean], renderFn: Double => VNode, toGraphChanges: Double => GraphChanges)(implicit ctx: Ctx.Owner): VNode =
    customInputField[Double](state, element, editMode, renderFn, StringOps.safeToDouble(_), toGraphChanges, VDomModifier(Elements.integerInputMod.toString, value := element.toString))
  def epochInputField(state: GlobalState, element: EpochMilli, editMode: Var[Boolean], renderFn: EpochMilli => VNode, toGraphChanges: EpochMilli => GraphChanges)(implicit ctx: Ctx.Owner): VNode =
    customInputField[EpochMilli](state, element, editMode, renderFn, wust.webUtil.StringOps.safeToEpoch(_), toGraphChanges, VDomModifier(Elements.dateInputMod, value := epochMilliToInputValue(element)))
  def customInputField[T](state: GlobalState, element: T, editMode: Var[Boolean], renderFn: T => VNode, stringToElement: String => Option[T], toGraphChanges: T => GraphChanges, inputMod: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {
    val initialRender: Var[VNode] = Var(renderFn(element))

    val editRender = editTextInputField[T](state, editMode, initialRender, renderFn, stringToElement, toGraphChanges).apply(inputMod)
    editOrRender(editMode, initialRender, editRender)
  }
}

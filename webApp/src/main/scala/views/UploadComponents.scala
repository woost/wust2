package wust.webApp.views

import wust.sdk.Colors
import wust.sdk.{ BaseColors, NodeColor }
import cats.effect.IO
import emojijs.EmojiConvertor
import fomanticui.{ SearchOptions, SearchSourceEntry, ToastOptions }
import fontAwesome._
import googleAnalytics.Analytics
import monix.execution.Cancelable
import monix.reactive.{ Observable, Observer }
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{ AttributeBuilder, EmitterBuilder }
import rx._
import jquery.JQuerySelection
import wust.api.UploadedFile
import wust.css.{ CommonStyles, Styles, ZIndex }
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util.macros.InlineList
import wust.util.StringOps._
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.jsdom.{ FileReaderOps, IntersectionObserver, IntersectionObserverOptions }
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{ EmojiReplacer, FocusPreference, GlobalState, PageChange, UploadingFile }
import wust.webApp.views.Elements._
import wust.webApp.views.UI.ModalConfig

import scala.collection.breakOut
import scala.scalajs.js
import WoostLogoComponents._

object UploadComponents {
  def renderUploadedFile(state: GlobalState, nodeId: NodeId, file: NodeData.File)(implicit ctx: Ctx.Owner): VNode = {
    import file._

    val maxImageHeight = "250px"

    def downloadUrl(attr: AttributeBuilder[String, VDomModifier]): VDomModifier = state.fileDownloadBaseUrl.map(_.map(baseUrl => attr := baseUrl + "/" + key))
    def preview(dataUrl: String): VDomModifier = {
      file.contentType match {
        case t if t.startsWith("image/") => img(height := maxImageHeight, src := dataUrl)
        case _                           => VDomModifier(height := "150px", width := "300px")
      }
    }
    def centerStyle = VDomModifier(
      Styles.flex,
      Styles.flexStatic,
      alignItems.center,
      flexDirection.column,
      justifyContent.spaceEvenly
    )
    def overlay = VDomModifier(
      background := "rgba(255, 255, 255, 0.8)",
      position.absolute,
      Styles.growFull
    )

    def downloadLink = a(downloadUrl(href), s"Download ${file.fileName}", onClick.stopPropagation --> Observer.empty)

    div(
      if (file.key.isEmpty) { // this only happens for currently-uploading files
        VDomModifier(
          file.fileName,
          Rx {
            val uploadingFiles = state.uploadingFiles()
            uploadingFiles.get(nodeId) match {
              case Some(UploadingFile.Error(dataUrl, retry)) => div(
                preview(dataUrl),
                position.relative,
                centerStyle,
                div(
                  overlay,
                  centerStyle,
                  div(freeSolid.faExclamationTriangle, " Error Uploading File"),
                  button(cls := "ui button", "Retry upload", onClick.stopPropagation.foreach { retry.runAsyncAndForget }, cursor.pointer)
                )
              )
              case Some(UploadingFile.Waiting(dataUrl)) => div(
                preview(dataUrl),
                position.relative,
                centerStyle,
                woostLoadingAnimation.apply(overlay, centerStyle)
              )
              case None => VDomModifier.empty
            }
          }
        )
      } else VDomModifier(
        p(downloadLink),
        contentType match {
          case t if t.startsWith("image/") =>
            val image = img(alt := fileName, downloadUrl(src), cls := "ui image")
            image(maxHeight := maxImageHeight, cursor.pointer, onClick.stopPropagation.foreach {
              state.uiModalConfig.onNext(Ownable(_ => ModalConfig(StringOps.trimToMaxLength(file.fileName, 20), image(cls := "fluid"), modalModifier = cls := "basic"))) //TODO: better size settings
              ()
            })
          //TODO pdf preview does not work with "content-disposition: attachment"-header
          //          case "application/pdf"           =>
          //            val embeddedPdf = htmlTag("object")(downloadUrl(data), dsl.tpe := "application/pdf")
          //            embeddedPdf(maxHeight := maxImageHeight, width := "100%")
          case _ => VDomModifier.empty
        }
      )
    )
  }

  def defaultFileUploadHandler(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): Var[Option[AWS.UploadableFile]] = {
    val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

    fileUploadHandler.foreach(_.foreach { uploadFile =>
      AWS.uploadFileAndCreateNode(state, uploadFile, nodeId => GraphChanges.addToParent(ChildId(nodeId), ParentId(focusedId)) merge GraphChanges.connect(Edge.LabeledProperty)(focusedId, EdgeData.LabeledProperty.attachment, PropertyId(nodeId))).foreach { _ =>
        fileUploadHandler() = None
      }
    })

    fileUploadHandler
  }

  def uploadFieldModifier(selected: Observable[Option[dom.File]], fileInputId: String, tooltipDirection: String = "top left")(implicit ctx: Ctx.Owner): VDomModifier = {

    val iconAndPopup: Observable[(VNode, Option[VNode])] = selected.prepend(None).map {
      case None =>
        (span(Icons.fileUpload), None)
      case Some(file) =>
        val popupNode = file.`type` match {
          case t if t.startsWith("image/") =>
            val dataUrl = dom.URL.createObjectURL(file)
            img(src := dataUrl, height := "100px", maxWidth := "400px") //TODO: proper scaling and size restriction
          case _ => div(file.name)
        }
        val icon = VDomModifier(
          Icons.fileUpload,
          color := "orange",
        )

        (span(icon), Some(popupNode))
    }

    val onDragOverModifier = Handler.unsafe[VDomModifier]

    VDomModifier(
      label(
        forId := fileInputId, // label for input will trigger input element on click.

        iconAndPopup.map {
          case (icon, popup) =>
            VDomModifier(
              popup.map(UI.popupHtml(tooltipDirection) := _),
              div(icon, cls := "icon")
            )
        },
        cls := "ui circular basic icon button",
        fontSize := "1.1em", // same size as submit-button in Chat/InputRow
      ),

      onDragOverModifier,
      onDragEnter.preventDefault(opacity := 0.5) --> onDragOverModifier,
      onDragLeave.preventDefault.onlyOwnEvents(VDomModifier.empty) --> onDragOverModifier,
      onDragOver.preventDefault --> Observer.empty,

      onDrop.preventDefault.foreach { ev =>
        val elem = document.getElementById(fileInputId).asInstanceOf[dom.html.Input]
        elem.files = ev.dataTransfer.files
      },
    )
  }

  def uploadField(state: GlobalState, selected: Var[Option[AWS.UploadableFile]])(implicit ctx: Ctx.Owner): VNode = {
    implicit val context = EditContext(state)

    EditableContent.editorRx[AWS.UploadableFile](selected, config = EditableContent.Config(
      errorMode = EditableContent.ErrorMode.ShowToast,
      submitMode = EditableContent.SubmitMode.Off
    )).apply(marginLeft := "3px")
  }
}

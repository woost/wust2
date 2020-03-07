package wust.webApp.views

import fontAwesome._
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp._
import wust.webApp.state.{GlobalState, UploadingFile}
import wust.webApp.views.WoostLogoComponents._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ModalConfig, Ownable}
import wust.webUtil.Elements.onClickDefault

object UploadComponents {
  def renderUploadedFile(nodeId: NodeId, file: NodeData.File)(implicit ctx: Ctx.Owner): VNode = {
    import file._

    val maxImageHeight = "250px"

    val downloadUrl: Option[String] = Client.wustFilesUrl.map(_ + "/" + key)
    def preview(dataUrl: String): VDomModifier = {
      if (file.isImage) img(height := maxImageHeight, src := dataUrl)
      else VDomModifier(height := "150px", width := "300px")
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

    def downloadLink = a(downloadUrl.map(href := _), s"Download ${file.fileName}", onClick.stopPropagation.discard)

    div(
      if (file.key.isEmpty) { // this only happens for currently-uploading files
        VDomModifier(
          file.fileName,
          Rx {
            val uploadingFiles = GlobalState.uploadingFiles()
            uploadingFiles.get(nodeId) match {
              case Some(UploadingFile.Error(dataUrl, retry)) => div(
                preview(dataUrl),
                position.relative,
                centerStyle,
                div(
                  overlay,
                  centerStyle,
                  div(freeSolid.faExclamationTriangle, " Error Uploading File"),
                  button(cls := "ui button", "Retry upload", onClickDefault.foreach { retry.runAsyncAndForget })
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
            val image = img(alt := fileName, downloadUrl.map(src := _), cls := "ui image")
            image(maxHeight := maxImageHeight, onClickDefault.foreach {
              GlobalState.uiModalConfig.onNext(Ownable(_ => ModalConfig(StringOps.trimToMaxLength(file.fileName, 20), image(cls := "fluid"), modalModifier = cls := "basic"))) //TODO: better size settings
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

  def defaultFileUploadHandler(focusedId: NodeId)(implicit ctx: Ctx.Owner): Var[Option[AWS.UploadableFile]] = {
    val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

    fileUploadHandler.foreach(_.foreach { uploadFile =>
      AWS.uploadFileAndCreateNode( uploadFile, nodeId => GraphChanges.addToParent(ChildId(nodeId), ParentId(focusedId)) merge GraphChanges.connect(Edge.LabeledProperty)(focusedId, EdgeData.LabeledProperty.attachment, PropertyId(nodeId))).foreach { _ =>
        fileUploadHandler() = None
      }
    })

    fileUploadHandler
  }

  def uploadField(acceptType: Option[String] = None)(implicit ctx: Ctx.Owner): EmitterBuilder[AWS.UploadableFile, VNode] = {
    EditableContent.editor[AWS.UploadableFile](config = EditableContent.Config(
      inputModifier = acceptType.map(accept := _),
      errorMode = EditableContent.ErrorMode.ShowToast,
      submitMode = EditableContent.SubmitMode.Manual,
    )).editValue.mapResult(_.apply(marginLeft := "3px"))
  }

  def uploadFieldRx(selected: Var[Option[AWS.UploadableFile]], acceptType: Option[String] = None)(implicit ctx: Ctx.Owner): VNode = {
    EditableContent.editorRx[AWS.UploadableFile](selected, config = EditableContent.Config(
      inputModifier = acceptType.map(accept := _),
      errorMode = EditableContent.ErrorMode.ShowToast,
      submitMode = EditableContent.SubmitMode.Manual
    )).apply(marginLeft := "3px")
  }
}

package wust.webApp.views

import cats.effect.IO
import fontAwesome.{IconDefinition, IconLookup, freeBrands, freeSolid}
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp.outwatchHelpers._
import rx.{Ctx, Rx, Var}
import wust.css.{CommonStyles, Styles}
import wust.graph.{Edge, GraphChanges, Node, Page}
import wust.ids._
import wust.webApp.dragdrop.DragItem
import wust.webApp.{BrowserDetect, Icons, Ownable}
import wust.webApp.state.{FocusPreference, GlobalState, PageChange}
import wust.external.trello
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import monix.reactive.Observable
import org.scalajs.dom
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.jsdom.FileReaderOps

import scala.collection.breakOut
import scala.concurrent.Future
import wust.util.macros.SubObjects

// A prompt to import from other platforms/files

object Importing {

  sealed trait Parser
  object Parser {
    type Result = IO[Either[String, NodeId => GraphChanges]]

    case class FromString(acceptType: Option[String], parse: String => Result) extends Parser
    object FromString {
      def apply(parse: String => Result): FromString = FromString(None, parse)
      def apply(acceptType: String)(parse: String => Result): FromString = FromString(Some(acceptType), parse)
    }
  }

  sealed trait Source {
    def icon: IconLookup
    def description: String
    def parser: Parser
  }
  object Source {
    case object TrelloFile extends Source {
      def icon = freeBrands.faTrello
      def description = "Trello Board (JSON)"
      def parser = Parser.FromString("application/json") { str => IO {
        decode[trello.Board](str) match {
          case Right(board) => Right(trello.Trello.translate(board, _))
          case Left(err) => Left(s"Trello JSON is invalid: $err")
        }
      }}
    }

    private def _all: List[Source] = macro SubObjects.list[Source] // TODO: fix missing full-name of objects in macro...
    val all = _all
  }

  case class Importer(title: String, result: Observable[Parser.Result], edit: VDomModifier)

  object Importer {

    def fromSource(source: Source)(implicit ctx: Ctx.Owner): Seq[Importer] = {
      source.parser match {
        case Parser.FromString(acceptType, parse) =>
          val config = EditableContent.Config(
            submitMode = EditableContent.SubmitMode.OnChange,
            errorMode = EditableContent.ErrorMode.ShowToast,
            innerModifier = acceptType.map(accept := _)
          )

          val currentFile = PublishSubject[Parser.Result]
          val currentText = PublishSubject[Parser.Result]

          Importer(
            "Upload file",
            currentFile,

            EditableContent.inputField[dom.File](config).editValueOption.map {
              case Some(file) => IO.fromFuture(IO(FileReaderOps.readAsText(file))).flatMap(parse)
              case None       => IO.pure(Left("Cannot read file"))
            } --> currentFile
          ) ::
            Importer(
              "Insert text",
              currentText,

              textArea(
                width := "100%",
                placeholder := source.description,
                onInput.value.map(parse) --> currentText,
                rows := 15,
              )
            ) ::
            Nil
      }
    }
  }

  val separatorColor = "rgba(0, 0, 0, 0.1)"

  val experimentalSign = div(
    "experimental",
    backgroundColor := "#F2711C",
    color := "white",
    borderRadius := "3px",
    padding := "0px 5px",
    fontWeight.bold,
    styles.extra.transform := "rotate(-7deg)",
  )

  def importerField(importer: Importer): EmitterBuilder[Parser.Result, VDomModifier] = EmitterBuilder.ofModifier { sink =>
    div(
      padding := "15px",
      Styles.growFull,
      Styles.flex,
      flexDirection.column,
      justifyContent.spaceBetween,

      div(
        div(importer.title + ":"),
        div(importer.edit, padding := "5px")
      ),

      button(
        cls := "ui primary button",
        dsl.cursor.pointer,
        "Import",
        disabled <-- importer.result.map(_ => false).take(1).prepend(true),
        onClick(importer.result) --> sink
      )
    )
  }

  def importerSeparator = {
    val line = div(backgroundColor := separatorColor, flexGrow := 1, width := "2px")
    div(
      width := "20px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      line,
      div("OR", Styles.flexStatic, padding := "5px 0px"),
      line
    )
  }

  // returns the modal config for rendering a modal for making an import
  def modalConfig(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): UI.ModalConfig = {
    val header: VDomModifier = Rx {
      state.rawGraph().nodesByIdGet(focusedId).map { node =>
        UI.ModalConfig.defaultHeader(state, node, "Import", Icons.`import`)
      }
    }

    val selectedSource = Var[Source](Source.all.head)
    val parserResult = PublishSubject[Parser.Result]

    val sourceSelector = Components.horizontalMenu(Source.all.map { source =>
      new Components.MenuItem(
        title = renderFontAwesomeIcon(source.icon),
        description = VDomModifier(fontSize.xSmall, source.description),
        active = selectedSource.map(_ == source),
        clickAction = () => selectedSource() = source
      )
    }).apply(textAlign.center)

    val description: VDomModifier = div(
      div(
        Styles.flex,
        alignItems.center,

        padding := "10px",
        backgroundColor := separatorColor,

        b("Import from:", marginRight := "50px"),

        sourceSelector(marginRight := "20px"),

        experimentalSign
      ),

      div(
        Styles.growFull,
        Styles.flex,
        padding := "10px",
        height := "400px",
        overflow.auto,

        selectedSource.map { source =>
          Importer.fromSource(source).foldLeft(Seq.empty[VDomModifier]) { (prev, importer) =>
            val field = importerField(importer).transform(_.flatMap { result =>
              Observable.fromIO(result).flatMap {
                case Right(changesf) =>
                  val nodeId = NodeId.fresh
                  val changes = changesf(nodeId) merge GraphChanges.addToParent(ChildId(nodeId), ParentId(focusedId))
                  UI.toast("Successfully imported Project")
                  state.urlConfig.update(_.focus(Page(nodeId), needsGet = false))
                  Observable(changes)
                case Left(error) =>
                  UI.toast(s"Failed to import: $error", level = UI.ToastLevel.Error)
                  Observable.empty
              }
            }) --> state.eventProcessor.changes

            if (prev.isEmpty) Seq(field) else prev ++ Seq(importerSeparator, field) // TODO: proper with css?
          }
        }
      )
    )

    UI.ModalConfig(header = header, description = description, contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for importing that opens the modal on click.
  def settingsItem(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "item",
      Elements.icon(Icons.`import`),
      span("Import"),

      dsl.cursor.pointer,
      onClick(Ownable(implicit ctx => modalConfig(state, focusedId))) --> state.uiModalConfig
    )
  }
}

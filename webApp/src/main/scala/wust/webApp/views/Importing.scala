package wust.webApp.views

import cats.effect.IO
import fontAwesome.freeSolid
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.experimental._
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx.{Ctx, Rx, Var}
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, ModalConfig, Ownable, UI}
import wust.css.Styles
import wust.external.{meistertask, trello, wunderlist}
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.util.collection._
import wust.webApp.{WoostConfig, Icons}
import wust.webApp.jsdom.FileReaderOps
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.scalajs.js.JSON
import scala.util.Try
import scala.util.control.NonFatal

// A prompt to import from other platforms/files

object Importing {
  //TODO: instruction how to export each source

  sealed trait Input
  object Input {
    final case class FromFile(description: VDomModifier, acceptType: Option[String]) extends Input
    final case class FromRemoteFile(description: VDomModifier, url: String => Option[String]) extends Input
    final case class FromText(description: VDomModifier) extends Input
  }

  object Parser {
    type Result = IO[Either[String, GraphChanges.Import]]
    type Function = String => Parser.Result
    type Provider = Rx[Option[Function]]
  }

  final case class Source(icon: VNode, title: String, description: String, inputs: List[Input], parser: Parser.Provider, form: VDomModifier)
  object Source {
    def apply(icon: VNode, title: String, description: String, inputs: List[Input], parser: Parser.Function): Source = Source(icon, title, description, inputs, Var(Some(parser)), VDomModifier.empty)

    def withForm[T](icon: VNode, title: String, description: String, inputs: List[Input], parser: Rx[Option[T]] => Parser.Provider, form: Var[Option[T]] => VDomModifier): Source = {
      val formVar = Var[Option[T]](None)
      Source(icon, title, description, inputs, parser(formVar), form(formVar))
    }

    def all(implicit ctx: Ctx.Owner): List[Source] = List(

      Source(
        icon = img(src := WoostConfig.value.urls.trelloIcon),
        title = "Trello",
        description = "Trello Board (JSON)",
        inputs = List(
          // Input.FromText("Paste the exported JSON from Trello here."),
          Input.FromFile("Upload the exported JSON file from Trello.", Some("application/json")),
          Input.FromRemoteFile("Paste the url of a public Trello Board.", { url =>
            Try(new URL(url)).toOption.collect {
              case uri if uri.host == "trello.com" && uri.pathname.startsWith("/b/") => uri.toString + ".json"
            }
          })
        ),
        parser = str => IO {
          trello.Trello.decodeJson(str).map(trello.Trello.translate(_))
        }
      ),

      Source(
        icon = img(src := WoostConfig.value.urls.wunderlistIcon),
        title = "Wunderlist",
        description = "Wunderlist Export (JSON)",
        inputs = List(
          // Input.FromText("Paste the exported JSON from Wunderlist here."),
          Input.FromFile("Upload the exported JSON file from Wunderlist.", Some("application/json")),
        ),
        parser = str => IO {
          wunderlist.Wunderlist.decodeJson(str).map(wunderlist.Wunderlist.translate(_))
        }
      ),

      Source.withForm[String](
        icon = img(src := WoostConfig.value.urls.meistertaskIcon),
        title = "MeisterTask",
        description = "MeisterTask Project (CSV)",
        inputs = List(
          // Input.FromText("Paste the exported CSV from MeisterTask here."),
          Input.FromFile("Upload the exported CSV file from MeisterTask.", Some("application/csv")),
        ),
        parser = _.map(_.map[String => Parser.Result] { projectName => str => IO {
          meistertask.MeisterTask.decodeCSV(projectName = projectName, csv = str).map(meistertask.MeisterTask.translate(_))
        }}),

        form = { formVar =>
          div(
            Styles.flex,
            alignItems.center,
            cls := "ui mini form",

            b("Project Name:", marginRight := "10px", Styles.flexStatic),
            EditableContent.editor[NonEmptyString](EditableContent.Config(
              submitMode = EditableContent.SubmitMode.OnInput,
              modifier = placeholder := "Project Name"
            )).editValueOption.map(_.map(_.string)) --> formVar
          )
        }
      ),

      Source(
        icon = Icons.tasks,
        title = "Tasklist",
        description = "Multiline Tasklist",
        inputs = List(
          Input.FromText("Enter a multiline tasklist, where each tasks is separated with a newline."),
          Input.FromFile("Upload text file with a multiline tasklist, where each task is separated with a newline.", Some("application/text")),
        ),
        parser = str => IO {
          val nodes = str.linesIterator.map[Node] { line => Node.MarkdownTask(line.trim) }.toIterable

          Right(GraphChanges.Import(
            GraphChanges(addNodes = nodes.to[Array]),
            topLevelNodeIds = nodes.to[Array].reverseMap(_.id),
            focusNodeId = None
          ))
        },
      ),

      Source(
        icon = Icons.csv,
        title = "CSV",
        description = "Table in CSV format",
        inputs = List(
          Input.FromText(s"Enter a table in CSV format (including a header line)."),
          Input.FromFile(s"Upload csv file with a table in CSV format (including a header line).", Some("application/csv")),
        ),
        parser = str => IO {
          CsvHelper.csvToChanges(str)
        },
      )
    )
  }

  final case class Importer(title: String, description: VDomModifier, result: Rx[Option[Parser.Result]], edit: VDomModifier)
  object Importer {

    private def combineParser(current: Rx[Option[IO[Either[String, String]]]], parser: Parser.Provider)(implicit ctx: Ctx.Owner): Rx[Option[Parser.Result]] = Rx {
      for {
        current <- current()
        parse <- parser()
      } yield current.flatMap {
        case Right(value) => parse(value)
        case Left(err) => IO.pure(Left(err))
      }
    }

    private def stringImporter(title: String, description: VDomModifier, parser: Parser.Provider)(form: Var[Option[IO[Either[String, String]]]] => VDomModifier)(implicit ctx: Ctx.Owner) = {
      val current = Var[Option[IO[Either[String, String]]]](None)
      Importer(
        title,
        description,
        combineParser(current, parser),
        form(current)
      )
    }

    def fromSource(source: Source)(implicit ctx: Ctx.Owner): List[Importer] = source.inputs.map {

     case Input.FromText(description) =>
       stringImporter("Insert text", description, source.parser) { current =>
         textArea(
           width := "100%",
           placeholder := source.description,
           onInput.value.map(s => Some(IO.pure(Right(s)))) --> current,
           rows := 15,
         )
       }

     case Input.FromFile(description, acceptType) =>
       stringImporter("Upload file", description, source.parser) { current =>
         EditableContent.editor[dom.File](EditableContent.Config(
           submitMode = EditableContent.SubmitMode.OnChange,
           modifier = acceptType.map(accept := _)
         )).editValueOption.map(_.map(file => IO.fromFuture(IO(FileReaderOps.readAsText(file))).map(Right(_)))) --> current
       }

     case Input.FromRemoteFile(description, getUrl) =>
       stringImporter("Import from Url", description, source.parser) { current =>
         EditableContent.editor[NonEmptyString](EditableContent.Config(
           submitMode = EditableContent.SubmitMode.OnInput,
           modifier = placeholder := "Url to public Trello board"
         )).mapResult(_.apply(cls := "ui mini form")).editValueOption.map(_.map { value =>
           getUrl(value.string) match {
             case Some(url) => IO.fromFuture(IO(
               dom.experimental.Fetch.fetch(url).toFuture
                 .flatMap { response => response.json().toFuture.map { json => Right(JSON.stringify(json))} }
                 .recover { case NonFatal(t) =>
                   Left(s"Cannot connect to URL '$url'")
                 }
             ))
             case None => IO.pure(Left("Invalid URL, expected a Trello Board URL."))
           }
         }) --> current
       }
    }
  }

  private val separatorColor = "rgba(0, 0, 0, 0.1)"

  private def importerForm(importer: Importer)(implicit ctx: Ctx.Owner): EmitterBuilder[Parser.Result, VDomModifier] = EmitterBuilder.ofModifier { sink =>
    div(
      padding := "15px",
      Styles.growFull,
      Styles.flex,
      flexDirection.column,
      justifyContent.spaceBetween,
      alignItems.center,

      div(
        div(importer.title + ":"),
        div(color.gray, fontSize.xSmall, importer.description, padding := "5px 0px"),
        div(importer.edit, padding := "5px")
      ),

      button(
        cls := "ui primary button",
        dsl.cursor.pointer,
        "Import",
        disabled <-- importer.result.map(_.isEmpty),
        onClick.mapTo(importer.result.now).collect { case Some(r) => r } --> sink
      )
    )
  }

  private def importerSeparator = {
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

  private def selectSource(sources: List[Source])(implicit ctx: Ctx.Owner): EmitterBuilder[Option[Source], VDomModifier] = EmitterBuilder.ofModifier { sink =>
    div(
      Styles.growFull,
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      padding := "10px",

      div(
        Styles.flex,
        alignItems.center,
        padding := "20px",

        h3("What do you want to import?"),
      ),

      div(
        Styles.flex,
        alignItems.center,
        flexWrap.wrap,

        sources.map { source =>
          button(
            margin := "10px",
            cls := "ui button",
            Styles.flex,
            flexDirection.column,
            alignItems.center,
            source.icon(height := "2em"),
            div(source.description, paddingTop := "3px"),
            cursor.pointer,
            onClick.stopPropagation(Some(source)) --> sink
          )
        }
      )
    )
  }

  private def renderSource(source: Source, changesObserver: Observer[GraphChanges.Import])(implicit ctx: Ctx.Owner): EmitterBuilder[Option[Source], VDomModifier] = EmitterBuilder.ofModifier { sink =>
    val importers = Importer.fromSource(source)
    div(
      Styles.flex,
      flexDirection.column,
      alignItems.center,

      div(padding := "10px", alignSelf.flexStart, freeSolid.faArrowLeft, cursor.pointer, onClick(None) --> sink),

      source.form,

      div(
        Styles.growFull,
        Styles.flex,
        justifyContent.center,
        padding := "5px 0",
        height := "400px",
        overflow.auto,

        importers.foldLeft(Seq.empty[VDomModifier]) { (prev, importer) =>
          val field = importerForm(importer).transform(_.flatMap { result =>
            Observable.from(result).flatMap {
              case Right(importChanges) =>
                UI.toast("Successfully imported Project")
                Observable(importChanges)
              case Left(error)     =>
                UI.toast(s"${StringOps.trimToMaxLength(error, 200)}", title = "Failed to import Project", level = UI.ToastLevel.Error)
                Observable.empty
            }
          }) --> changesObserver

          if(prev.isEmpty) Seq(field) else prev ++ Seq(importerSeparator, field) // TODO: proper with css?
        }
      ),
    )
  }

  private def renderSourceHeader(source: Option[Source]) = source match {

    case Some(source) => VDomModifier(
      span(
        Styles.flex,
        alignItems.center,
        padding := "6px",
        backgroundColor := "white",
        source.icon(height := "1em", Styles.flexStatic),
        marginRight := "10px"
      ),
      span(s"Import from ${source.description}", marginRight := "10px")
    )

    case None => span("Import your data", marginRight := "10px")

  }

  private def renderSourceBody(source: Option[Source], changesObserver: Observer[GraphChanges.Import], allSources: List[Source])(implicit ctx: Ctx.Owner) = source match {
    case Some(source) => renderSource(source, changesObserver)
    case None => selectSource(allSources)
  }

  // returns the modal config for rendering a modal for making an import
  def modalConfig(focusedId: NodeId)(implicit ctx: Ctx.Owner): ModalConfig = {
    val selectedSource = Var[Option[Source]](None)
    val allSources = Source.all

    val changesObserver = GlobalState.eventProcessor.changes.contramap[GraphChanges.Import] { importChanges =>
      //TODO: do not sideeffect with state changes here...
      importChanges.focusNodeId.foreach { focusNodeId =>
        GlobalState.urlConfig.update(_.focus(Page(focusNodeId), needsGet = false))
      }

      GlobalState.uiModalClose.onNext(())

      importChanges.resolve(GlobalState.graph.now, focusedId)
    }

    val modalHeader: VDomModifier = Rx {
      GlobalState.rawGraph().nodesById(focusedId).map { node =>
        Modal.defaultHeader(
          node,
          modalHeader = div(
            Styles.flex,
            flexWrap.wrap,
            alignItems.center,
            selectedSource.map(renderSourceHeader),
            Components.experimentalSign(color = "white").apply(fontSize.xSmall)
          ),
          icon = Icons.`import`
        )
      }
    }

    val modalDescription: VDomModifier = div(
      selectedSource.map(renderSourceBody(_, changesObserver, allSources) --> selectedSource)
    )

    ModalConfig(header = modalHeader, description = modalDescription, contentModifier = VDomModifier(styleAttr := "padding : 0px !important")) // overwrite padding of modal
  }

  // a settings button for importing that opens the modal on click.
  def settingsItem(focusedId: NodeId)(implicit ctx: Ctx.Owner): VNode = {
    a(
      cls := "item",
      Elements.icon(Icons.`import`),
      span("Import"),

      dsl.cursor.pointer,
      onClick(Ownable(implicit ctx => modalConfig(focusedId))) --> GlobalState.uiModalConfig
    )
  }
}

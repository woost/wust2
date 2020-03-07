package wust.webApp.views

import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
import outwatch.reactive.handler._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.state.{ FocusState, GlobalState }
import wust.webApp.views.Components._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js

class ContainerSink[T] {
  private var array = new js.Array[js.UndefOr[T]]

  private val isEmptyHandler = Subject.behavior[Boolean](true)
  val isEmptySource: Observable[Boolean] = isEmptyHandler.distinctOnEquals

  def register(): Observer[T] = {
    val index = array.length
    array.push(js.undefined)
    Observer.create[T] { value =>
      array(index) = value
      isEmptyHandler.onNext(false)
    }
  }

  def clear(): Unit = {
    array = new js.Array[js.UndefOr[T]]
    isEmptyHandler.onNext(true)
  }

  def isEmpty: Boolean = array.forall(_.isEmpty)

  def currentValues(): js.Array[T] = {
    val result = new js.Array[T]
    array.foreach(_.foreach(result.push(_)))
    result
  }
}

object FormView {

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val container = new ContainerSink[GraphChanges]

    val node = Rx {
      val graph = GlobalState.rawGraph()
      graph.nodesByIdOrThrow(focusState.focusedId)
    }

    val propertySingle = Rx {
      val graph = GlobalState.rawGraph()
      graph.idToIdxMap(focusState.focusedId) { nodeIdx =>
        PropertyData.Single(graph, nodeIdx)
      }
    } //.filter(_ => container.isEmpty)

    val propertiesIsEmpty = propertySingle.map(_.forall(_.properties.isEmpty))

    val titleEditMode = Var(false)

    form(
      minWidth := "400px",
      Styles.growFull,
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      padding := "20px",
      onSubmit.preventDefault.discard,
      cls := "ui form",

      h3(
        minHeight := "20px",
        minWidth := "20px",

        onClickDefault.use(true) --> titleEditMode,

        node.map { node =>
          EditableContent.inlineEditorOrRender[String](node.settingsOrDefault.formOrDefault.title.getOrElse("Form"), titleEditMode, _ => s => s).editValue.foreach { str =>
            node match {
              case node: Node.Content =>
                val newNode = node.updateSettings(_.updateForm(_.copy(title = Some(str))))
                GlobalState.submitChanges(GraphChanges.addNode(newNode))
              case _ => ()
            }
          }
        }
      ),

      table(
        width := "100%",

        tbody(
          Rx {
            container.clear()

            if (propertiesIsEmpty()) div(
              textAlign.center,
              opacity := 0.5,
              "This item does not have any fields yet. You can add custom fields by clicking on the project title to open it in the right side bar."
            )
            else VDomModifier(
              propertySingle().map(propertySingle => propertySingle.properties.map { property =>
                propertyRow(property.key, property.values, container)
              })
            )
          }
        )
      ),

      Rx {
        VDomModifier.ifNot(propertiesIsEmpty())(
          div(
            alignSelf.flexEnd,

            Styles.flex,
            alignItems.center,
            container.isEmptySource.map{ isSaved =>
              if (isSaved) span("saved.", opacity := 0.5)
              else span("The form has unsaved changes.")
            },
            button (
              margin := "10px",
              cls := "ui button primary",
              disabled <-- container.isEmptySource,
              "Save",
              onClickDefault.foreach { _ =>
                val current = container.currentValues()
                if (current.nonEmpty) {
                  GlobalState.submitChanges(current.reduce(_ merge _))
                }
              }
            ),
          )
        )
      }
    )
  }

  private def propertyRow(
    key: String,
    properties: Seq[PropertyData.PropertyValue],
    container: ContainerSink[GraphChanges]
  )(implicit ctx: Ctx.Owner): VNode = {

    tr(
      padding := "5px",
      width := "100%",

      td(b(key + ":"), display.inlineFlex, marginRight := "10px"),

      td(
        properties.map { property =>
          val localChanges = container.register()

          div(
            margin := "3px 0px",
            editablePropertyCell(property.node, property.edge) --> localChanges
          )
        }
      )
    )
  }

  private def editablePropertyCell(node: Node, edge: Edge.LabeledProperty)(implicit ctx: Ctx.Owner): EmitterBuilder[GraphChanges, VNode] = EmitterBuilder.ofNode { sink =>
    val config = EditableContent.Config(
      submitMode = EditableContent.SubmitMode.Manual,
      emitter = onInput,
      autoFocus = false,
    )

    def contentEditor = EditableContent.ofNode(node, config).editValue.map(GraphChanges.addNode) --> sink

    def refEditor = EditableContent.custom[NodeId](
      node.id,
      implicit ctx => handler => searchAndSelectNodeApplied[Handler](
        ProHandler(
          handler.edit.contramap[Option[NodeId]](EditInteraction.fromOption(_)),
          handler.edit.collect[Option[NodeId]] { case EditInteraction.Input(id) => Some(id) }.prepend(Some(node.id)).replay.refCount
        ),
        filter = (n: Node) => true,
      ),
      config
    ).editValue.collect {
        case newNodeId if newNodeId != edge.propertyId =>
          GraphChanges(delEdges = Array(edge), addEdges = Array(edge.copy(propertyId = PropertyId(newNodeId))))
      } --> sink

    div(
      (node.role, node.data) match {
        case (_, NodeData.Placeholder(Some(NodeTypeSelection.Ref))) => refEditor
        case (_, NodeData.Placeholder(Some(NodeTypeSelection.Data(_)))) => contentEditor
        case (NodeRole.Neutral, _) => contentEditor
        case (_, _) => refEditor
      }
    )
  }

}

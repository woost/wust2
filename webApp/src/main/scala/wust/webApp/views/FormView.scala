package wust.webApp.views

import wust.webApp.state.FeatureState
import outwatch.dom._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.dom.dsl._
import outwatch.reactive._
import outwatch.reactive.handler._
import outwatch.ext.monix._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Ownable}
import wust.css.Styles
import wust.graph._
import wust.webApp.Icons
import wust.ids.{Feature, _}
import wust.webApp.dragdrop.DragContainer
import wust.webApp.state.{FocusState, GlobalState, Placeholder, TraverseState}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.registerDragContainer
import scala.scalajs.js

class ContainerSink[T] {
  private var array = new js.Array[js.UndefOr[T]]

  private val isEmptyHandler = SinkSourceHandler[Boolean](true)
  @inline def isEmptySource: SourceStream[Boolean] = isEmptyHandler.distinctOnEquals

  def register(): SinkObserver[T] = {
    val index = array.length
    array.push(js.undefined)
    SinkObserver.create[T] { value =>
      println("JO " + value)
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
  import SharedViewElements._

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val container = new ContainerSink[GraphChanges]

    val propertySingle = Rx {
      val graph = GlobalState.rawGraph()
      graph.idToIdxMap(focusState.focusedId) { nodeIdx =>
        PropertyData.Single(graph, nodeIdx)
      }
    }//.filter(_ => container.isEmpty)

    container.isEmptySource.foreach { s => println("CHA " + s) }

    form(
      Styles.growFull,
      Styles.flex,
      flexDirection.column,
      alignItems.flexStart,
      padding := "20px",
      onSubmit.preventDefault.discard,
      cls := "ui form",

      Rx {
        container.clear()

        propertySingle().map { propertySingle =>
          propertySingle.properties.map { property =>
            propertySection(property.key, property.values, container)
          }
        }
      },

      button(
        margin := "10px",
        cls := "ui button primary",
        disabled <-- container.isEmptySource,
        "Save",
        onClick.stopPropagation.foreach { _ =>
          val current = container.currentValues()
          if (current.nonEmpty) {
            GlobalState.submitChanges(current.reduce(_ merge _))
          }
        }
      )
    )
  }

  def propertySection(
    key: String,
    properties: Seq[PropertyData.PropertyValue],
    container: ContainerSink[GraphChanges]
  )(implicit ctx: Ctx.Owner): VNode = {

    div(
      padding := "5px",
      width := "100%",
      Styles.wordWrap,
      Styles.flex,
      flexWrap.wrap,
      alignItems.flexStart,
      justifyContent.spaceBetween,

      b(key + ":"),

      div(
        flexGrow := 1,

        properties.map { property =>
          val localChanges = container.register()

          div(
            marginLeft := "20px",
            Styles.flex,
            justifyContent.spaceBetween,

            div(
              Styles.flex,
              justifyContent.flexEnd,
              margin := "3px 0px",

              editablePropertyNode( property.node, property.edge) --> localChanges
            )
          )
        }
      )
    )
  }

  private def editablePropertyNode(node: Node, edge: Edge.LabeledProperty)(implicit ctx: Ctx.Owner): EmitterBuilder[GraphChanges, VNode] = EmitterBuilder.ofNode { sink =>
    val config = EditableContent.Config(
      submitMode = EditableContent.SubmitMode.Manual,
      emitter = onInput,
      autoFocus = false,
    )

    def contentEditor = EditableContent.ofNode(node, config).editValue.map(GraphChanges.addNode) --> sink

    def refEditor = EditableContent.custom[Node](
      node,
      implicit ctx => handler => searchAndSelectNodeApplied[Handler](
        ProHandler(
          handler.edit.contracollect[Option[NodeId]] { case id => EditInteraction.fromOption(id.map(GlobalState.rawGraph.now.nodesByIdOrThrow(_))) },
          handler.edit.collect[Option[NodeId]] { case EditInteraction.Input(v) => Some(v.id) }.prepend(Some(node.id)),
        ),
        filter = (_:Node) => true,
      ),
      config
    ).editValue.collect { case newNode if newNode.id != edge.propertyId =>
      GraphChanges(delEdges = Array(edge), addEdges = Array(edge.copy(propertyId = PropertyId(newNode.id))))
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

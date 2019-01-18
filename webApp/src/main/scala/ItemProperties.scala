package wust.webApp

import monix.reactive.Observable
import monix.reactive.subjects.{BehaviorSubject, PublishSubject}
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.BaseColors
import wust.sdk.NodeColor.hue
import wust.util.StringOps._
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.{Components, Elements, UI}
import wust.webUtil.StringOps._

import scala.scalajs.js
import scala.collection.breakOut

/*
 * Here, the managing of node properties is done.
 * This is done via a simple form.
 * Either just get the form: managePropertiesContent
 * Or get a dropdown of this content: managePropertiesDropdown
 */
object ItemProperties {

  val naming = "Custom fields"
  val defaultType = NodeData.PlainText.tpe

  def iconByNodeData(data: NodeData): VDomModifier = data match {
    //    case _: NodeData.Integer => Icons.propertyInt
    //    case _: NodeData.Float => Icons.propertyDec
    case _: NodeData.Integer | _: NodeData.Decimal => Icons.propertyNumber
    case _: NodeData.Date                          => Icons.propertyDate
    case _: NodeData.File                          => Icons.files
    case _                                         => Icons.propertyText
  }

  def managePropertiesContent(state: GlobalState, nodeId: NodeId, prefilledType: Option[NodeData.Type] = Some(defaultType), prefilledKey: String = "", targetNodeIds: Option[Array[NodeId]] = None, extendNewProperty: (EdgeData.LabeledProperty, Node.Content) => GraphChanges = (_, _) => GraphChanges.empty)(implicit ctx: Ctx.Owner): VDomModifier = {

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val propertyTypeSelection = BehaviorSubject[NodeData.Type](prefilledType getOrElse NodeData.Empty.tpe).transformObservable(o => Observable(o, clear.map(_ => NodeData.Empty.tpe)).merge)
    val propertyKeyInputProcess = BehaviorSubject[String](prefilledKey).transformObservable(o => Observable(o, clear.map(_ => "")).merge)
    val propertyValueInputProcess = BehaviorSubject[String]("").transformObservable(o => Observable(o, clear.map(_ => "")).merge)

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Element = null
      val inputSizeMods = VDomModifier(width := "200px", marginTop := "4px")

      val inputFieldMod: NodeData.Type => VDomModifier = {
        case NodeData.Integer.tpe   => Elements.integerInputMod
        case NodeData.Decimal.tpe   => Elements.decimalInputMod
        case NodeData.Date.tpe      => Elements.dateInputMod
        case NodeData.PlainText.tpe => Elements.textInputMod
        case _                      =>  VDomModifier(disabled, placeholder := "Select a field type")
      }

      VDomModifier(
        form(
          onDomMount.asHtml.foreach { element = _ },
          Styles.flex,
          flexDirection.column,
          alignItems.center,
          input(
            cls := "ui fluid action input",
            inputSizeMods,
            tpe := "text",
            placeholder := "Field Name",
            cls <-- propertyTypeSelection.map(t => if(t == NodeData.Empty.tpe) "disabled" else ""),
            onInput.value --> propertyKeyInputProcess,
            value <-- propertyKeyInputProcess
          ),
          input(
            cls := "ui fluid action input",
            inputSizeMods,
            value <-- clear,
            onInput.value --> propertyValueInputProcess,
            cls <-- propertyKeyInputProcess.map(k => if(k.isEmpty) "disabled" else ""),
            propertyTypeSelection.map(inputFieldMod),
            Elements.valueWithEnter(propertyValueInputProcess.withLatestFrom2(propertyKeyInputProcess, propertyTypeSelection)((pValue, pKey, pType) => (pKey, pValue, pType))) foreach { propertyData =>
              if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
                handleAddProperty(propertyData._1, propertyData._2, propertyData._3)
              }
            },
          ),
          select(
            inputSizeMods,
            option(
              value := "none", "Select a field type",
              selected <-- propertyTypeSelection.map(_ == NodeData.Empty.tpe),
              disabled,
            ),
            option( value := NodeData.Integer.tpe, "Integer Number", selected <-- propertyTypeSelection.map(_ == NodeData.Integer.tpe)),
            option( value := NodeData.Decimal.tpe, "Decimal Number", selected <-- propertyTypeSelection.map(_ == NodeData.Decimal.tpe)),
            option( value := NodeData.Date.tpe, "Date", selected <-- propertyTypeSelection.map(_ == NodeData.Date.tpe)),
            option( value := NodeData.PlainText.tpe, "Text", selected <-- propertyTypeSelection.map(_ == NodeData.PlainText.tpe)),
            onInput.value.map(_.asInstanceOf[NodeData.Type]) --> propertyTypeSelection,
          ),
          div(
            cls := "ui primary button approve",
            cls <-- propertyValueInputProcess.map(v => if(v.isEmpty) "disabled" else ""),
            inputSizeMods,
            "Add Custom Field",
            onClick(propertyValueInputProcess.withLatestFrom2(propertyKeyInputProcess, propertyTypeSelection)((pValue, pKey, pType) => (pKey, pValue, pType))) foreach { propertyData =>
              if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
                handleAddProperty(propertyData._1, propertyData._2, propertyData._3)
              }
            },
          ),
          targetNodeIds.map { targetNodeIds =>
            VDomModifier.ifTrue(targetNodeIds.size > 1)(i(
              padding := "4px",
              s"* The properties you set here will be applied to ${targetNodeIds.size} nodes."
            ))
          }
        ),
      )
    }

    def handleAddProperty(propertyKey: String, propertyValue: String, propertyType: String)(implicit ctx: Ctx.Owner): Unit = {

      // TODO: Users and reuse
      val propertyNodeOpt: Option[Node.Content] = propertyType match {
        case NodeData.Integer.tpe   => safeToInt(propertyValue).map(number => Node.Content(NodeData.Integer(number), NodeRole.Neutral))
        case NodeData.Decimal.tpe   => safeToDouble(propertyValue).map(number => Node.Content(NodeData.Decimal(number), NodeRole.Neutral))
        case NodeData.Date.tpe      => safeToEpoch(propertyValue).map(datum => Node.Content(NodeData.Date(datum), NodeRole.Neutral))
        case NodeData.PlainText.tpe => Some(Node.Content(NodeData.PlainText(propertyValue), NodeRole.Neutral))
        case _                      => None
      }

      propertyNodeOpt.foreach { propertyNode =>

        val propertyEdgeData = EdgeData.LabeledProperty(propertyKey)
        def addProperty(targetNodeId: NodeId): GraphChanges = {
          val newPropertyNode = propertyNode.copy(id = NodeId.fresh)
          val propertyEdge = Edge.LabeledProperty(targetNodeId, propertyEdgeData, PropertyId(newPropertyNode.id))
          GraphChanges(addNodes = Set(newPropertyNode), addEdges = Set(propertyEdge))
        }

        val changes = targetNodeIds match {
          case Some(targetNodeIds) =>
            val graph = state.graph.now
            val changes = targetNodeIds.map { targetNodeId =>
              val alreadyExists = state.graph.now.propertiesEdgeIdx.exists(graph.idToIdxOrThrow(targetNodeId)) { edgeIdx =>
                graph.edges(edgeIdx).asInstanceOf[Edge.LabeledProperty].data.key == propertyKey
              }

              if (alreadyExists) GraphChanges.empty else addProperty(targetNodeId)
            }

            changes.fold(GraphChanges.empty)(_ merge _) merge extendNewProperty(propertyEdgeData, propertyNode)
          case None => addProperty(nodeId) merge extendNewProperty(propertyEdgeData, propertyNode)
        }

        state.eventProcessor.changes.onNext(changes) foreach { _ => clear.onNext (()) }
      }
    }

    def propertyRow(propertyKey: Edge.LabeledProperty, propertyValue: Node)(implicit ctx: Ctx.Owner): VNode = div(
        Styles.flex,
        alignItems.center,
      Components.removablePropertyTag(state, propertyKey, propertyValue),
        )

    description
  }

  def managePropertiesDropdown(state: GlobalState, nodeId: NodeId, prefilledType: Option[NodeData.Type] = Some(defaultType), prefilledKey: String = "", targetNodeIds: Option[Array[NodeId]] = None, descriptionModifier: VDomModifier = VDomModifier.empty, dropdownModifier: VDomModifier = cls := "top left", extendNewProperty: (EdgeData.LabeledProperty, Node.Content) => GraphChanges = (_, _) => GraphChanges.empty)(implicit ctx: Ctx.Owner): VNode = {
    UI.dropdownMenu(VDomModifier(
      padding := "5px",
      div(cls := "item", display.none), // dropdown menu needs an item
      div(
        cls := "ui mini form",
        managePropertiesContent(state, nodeId, prefilledType, prefilledKey, targetNodeIds, extendNewProperty),
        descriptionModifier
      )
    ), dropdownModifier = dropdownModifier)
  }
}


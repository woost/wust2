package wust.webApp

import monix.reactive.{Observable, Observer}
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
import wust.webApp.views.{Components, Elements, UI, EditableContent, EditInputParser, EditInteraction, EditContext}
import wust.webApp.StringJsOps._

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

  def iconByNodeData(data: NodeData): Option[VNode] = Some(data) collect {
    case _: NodeData.Integer | _: NodeData.Decimal   => Icons.propertyNumber
    case _: NodeData.Date | _: NodeData.RelativeDate => Icons.propertyDate
    case _: NodeData.File                            => Icons.files
  }

  def managePropertiesContent(state: GlobalState, nodeId: NodeId, prefilledType: Option[NodeData.Type] = Some(defaultType), prefilledKey: String = "", targetNodeIds: Option[Array[NodeId]] = None, extendNewProperty: (EdgeData.LabeledProperty, Node.Content) => GraphChanges = (_, _) => GraphChanges.empty)(implicit ctx: Ctx.Owner): VDomModifier = {

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val propertyTypeSelection = Var[Option[NodeData.Type]](prefilledType)
    val propertyKeyInput = Var[Option[NonEmptyString]](NonEmptyString(prefilledKey))
    val propertyValueInput = Var[Option[NodeData]](None)
    propertyTypeSelection.foreach { selection =>
      if (propertyKeyInput.now.isEmpty) selection.foreach {
        case NodeData.File.tpe => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.attachment.key)
        case _ => ()
      }
    }

    val editableConfig = EditableContent.Config(
      outerModifier = VDomModifier(width := "100%"),
      submitMode = EditableContent.SubmitMode.OnInput,
      selectTextOnFocus = false
    )
    implicit val context = EditContext(state)

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Element = null

      val isAutomationTemplate = Rx {
        val graph = state.graph()
        graph.automatedNodes(graph.idToIdxOrThrow(nodeId)).nonEmpty
      }

      def createProperty() = {
        if(element.asInstanceOf[js.Dynamic].reportValidity().asInstanceOf[Boolean]) {
          handleAddProperty(propertyKeyInput.now, propertyValueInput.now)
        }
      }

      VDomModifier(
        form(
          width := "200px",
          onDomMount.asHtml.foreach { element = _ },
          Styles.flex,
          flexDirection.column,
          alignItems.center,
          VDomModifier.ifTrue(propertyKeyInput.now.isEmpty)( //do not select key if already specifided
            EditableContent.inputFieldRx[NonEmptyString](propertyKeyInput, editableConfig.copy(
              innerModifier = VDomModifier(
                width := "100%",
                Elements.onEnter.stopPropagation foreach(createProperty()),
                placeholder := "Field Name"
              ),
            ))
          ),
          div(
            marginTop := "4px",
            width := "100%",
            Styles.flex,
            alignItems.center,
            justifyContent.spaceBetween,
            b("Field Type:", color.gray, margin := "0px 5px 0px 5px"),
            isAutomationTemplate.map { isTemplate =>
              EditableContent.select[NodeData.Type](
                "Select a field type",
                propertyTypeSelection,
                ("Text", NodeData.PlainText.tpe) ::
                ("Integer Number", NodeData.Integer.tpe) ::
                ("Decimal Number", NodeData.Decimal.tpe) ::
                ("File", NodeData.File.tpe) ::
                ("Date", NodeData.Date.tpe) ::
                (if (isTemplate) ("Relative Date", NodeData.RelativeDate.tpe) :: Nil else Nil)
              ).apply(tabIndex := -1)
            }
          ),
          propertyTypeSelection.map(_.flatMap { propertyType =>
            EditInputParser.forNodeDataType(propertyType) map { implicit parser =>
              propertyValueInput() = None// clear value on each type change...
              EditableContent.inputFieldRx[NodeData](propertyValueInput, editableConfig.copy(
                innerModifier = VDomModifier(
                  width := "100%",
                  marginTop := "4px",
                  Elements.onEnter.stopPropagation foreach(createProperty())
                )
              ))
            }
          }),
          div(
            marginTop := "5px",
            cls := "ui primary button approve",
            Rx {
              VDomModifier.ifTrue(propertyKeyInput().isEmpty || propertyValueInput().isEmpty || targetNodeIds.exists(_.isEmpty))(cls := "disabled")
            },
            "Add Custom Field",
            onClick.stopPropagation foreach(createProperty())
          ),
          targetNodeIds.map { targetNodeIds =>
            i(
              padding := "4px",
              whiteSpace.normal,
              s"* The properties you set here will be applied to ${targetNodeIds.size} nodes."
            )
          }
        ),
      )
    }

    def handleAddProperty(propertyKey: Option[NonEmptyString], propertyValue: Option[NodeData])(implicit ctx: Ctx.Owner): Unit = for {
      propertyKey <- propertyKey
      propertyValue <- propertyValue
    } {

      val propertyNodeOpt: Option[Node.Content] = propertyValue match {
        case data: NodeData.Content     => Some(Node.Content(nodeId, data, NodeRole.Neutral))
        case _                          => None
      }

      propertyNodeOpt.foreach { propertyNode =>

        val propertyEdgeData = EdgeData.LabeledProperty(propertyKey.string)
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
                graph.edges(edgeIdx).asInstanceOf[Edge.LabeledProperty].data.key == propertyKey.string
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

  def managePropertiesDropdown(state: GlobalState, nodeId: NodeId, prefilledType: Option[NodeData.Type] = Some(defaultType), prefilledKey: String = "", targetNodeIds: Option[Array[NodeId]] = None, descriptionModifier: VDomModifier = VDomModifier.empty, dropdownModifier: VDomModifier = cls := "top left", extendNewProperty: (EdgeData.LabeledProperty, Node.Content) => GraphChanges = (_, _) => GraphChanges.empty)(implicit ctx: Ctx.Owner): VDomModifier = {
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


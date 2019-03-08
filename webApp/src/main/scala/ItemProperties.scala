package wust.webApp

import monix.eval.Task
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
import wust.util.macros.InlineList
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.{Components, Elements, UI, EditableContent, EditInputParser, EditStringParser, ValueStringifier, EditInteraction, EditContext, EditImplicits}
import wust.webApp.StringJsOps._

import scala.scalajs.js
import scala.collection.breakOut
import scala.util.Try

/*
 * Here, the managing of node properties is done.
 * This is done via a simple form.
 * Either just get the form: managePropertiesContent
 * Or get a dropdown of this content: managePropertiesDropdown
 */
object ItemProperties {

  sealed trait TypeSelection
  object TypeSelection {
    case class Data(data: NodeData.Type) extends TypeSelection
    case object Ref extends TypeSelection

    import wust.ids.serialize.Circe._
    import io.circe._, io.circe.generic.auto._

    implicit val parser: EditStringParser[TypeSelection] = EditImplicits.circe.StringParser[TypeSelection]
    implicit val stringifier: ValueStringifier[TypeSelection] = EditImplicits.circe.Stringifier[TypeSelection]
  }

  val naming = "Custom fields"
  val defaultType = NodeData.Markdown.tpe

  def iconByNodeData(data: NodeData): Option[VNode] = Some(data) collect {
    case _: NodeData.Integer | _: NodeData.Decimal   => Icons.propertyNumber
    case _: NodeData.Date | _: NodeData.RelativeDate => Icons.propertyDate
    case _: NodeData.File                            => Icons.files
  }

  def managePropertiesContent(state: GlobalState, nodeId: NodeId, prefilledType: Option[NodeData.Type] = Some(defaultType), prefilledKey: String = "", targetNodeIds: Option[Array[NodeId]] = None, extendNewProperty: (EdgeData.LabeledProperty, Either[NodeId, Node.Content]) => GraphChanges = (_, _) => GraphChanges.empty)(implicit ctx: Ctx.Owner): VDomModifier = {

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val propertyTypeSelection = Var[Option[TypeSelection]](prefilledType.map(TypeSelection.Data(_)))
    val propertyKeyInput = Var[Option[NonEmptyString]](NonEmptyString(prefilledKey))
    val propertyValueInput = Var[Option[Either[NodeId, NodeData]]](None)
    propertyTypeSelection.foreach { selection =>
      propertyValueInput() = None// clear value on each type change...
      if (propertyKeyInput.now.isEmpty) selection.foreach {
        case TypeSelection.Data(NodeData.File.tpe) => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.attachment.key)
        case TypeSelection.Ref => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.reference.key)
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

      val isChildOfAutomationTemplate = Rx {
        val graph = state.graph()
        val nodeIdx = graph.idToIdxOrThrow(nodeId)
        graph.automatedNodes(nodeIdx).nonEmpty || graph.ancestorsIdx(nodeIdx).exists(parentIdx => graph.automatedNodes(parentIdx).nonEmpty)
      }

      def createProperty() = {
        handleAddProperty(propertyKeyInput.now, propertyValueInput.now)
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
            isChildOfAutomationTemplate.map { isTemplate =>
              EditableContent.select[TypeSelection](
                "Select a field type",
                propertyTypeSelection,
                ("Text", TypeSelection.Data(NodeData.Markdown.tpe)) ::
                ("Number", TypeSelection.Data(NodeData.Decimal.tpe)) ::
                ("File", TypeSelection.Data(NodeData.File.tpe)) ::
                ("Date", TypeSelection.Data(NodeData.Date.tpe)) ::
                (if (isTemplate) ("Relative Date", TypeSelection.Data(NodeData.RelativeDate.tpe)) :: Nil else Nil) :::
                ("Refer to...", TypeSelection.Ref) ::
                Nil
              ).apply(tabIndex := -1)
            }
          ),
          propertyTypeSelection.map(_.flatMap {
            case TypeSelection.Data(propertyType) =>
              EditInputParser.forNodeDataType(propertyType) map { implicit parser =>
                EditableContent.inputFieldRx[NodeData](propertyValueInput.imap[Option[NodeData]](_.collect { case Right(data) => data })(_.map(Right(_))), editableConfig.copy(
                  innerModifier = VDomModifier(
                    width := "100%",
                    marginTop := "4px",
                    Elements.onEnter.stopPropagation foreach(createProperty())
                  )
                ))
              }
            case TypeSelection.Ref => Some(
              Components.searchAndSelectNode(state, propertyValueInput.toObservable.map(_.flatMap(_.left.toOption)), opt => propertyValueInput() = opt.map(Left(_))).apply(
                width := "100%",
                marginTop := "4px",
              )
            )
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

    def handleAddProperty(propertyKey: Option[NonEmptyString], propertyValue: Option[Either[NodeId, NodeData]])(implicit ctx: Ctx.Owner): Unit = for {
      propertyKey <- propertyKey
      propertyValue <- propertyValue
    } {

      val propertyEdgeData = EdgeData.LabeledProperty(propertyKey.string)

      def sendChanges(addProperty: NodeId => GraphChanges, extendable: Either[NodeId, Node.Content]) = {
        val changes = targetNodeIds match {
          case Some(targetNodeIds) =>
            val graph = state.graph.now
            val changes = targetNodeIds.map { targetNodeId =>
              val alreadyExists = state.graph.now.propertiesEdgeIdx.exists(graph.idToIdxOrThrow(targetNodeId)) { edgeIdx =>
                graph.edges(edgeIdx).asInstanceOf[Edge.LabeledProperty].data.key == propertyKey.string
              }

              if (alreadyExists) GraphChanges.empty else addProperty(targetNodeId)
            }

            changes.fold(GraphChanges.empty)(_ merge _) merge extendNewProperty(propertyEdgeData, extendable)
          case None => addProperty(nodeId) merge extendNewProperty(propertyEdgeData, extendable)
        }

        state.eventProcessor.changes.onNext(changes) foreach { _ => clear.onNext (()) }
      }

      propertyValue match {

        case Right(data: NodeData.Content) =>
          val propertyNode = Node.Content(nodeId, data, NodeRole.Neutral)
          def addProperty(targetNodeId: NodeId): GraphChanges = {
            val newPropertyNode = propertyNode.copy(id = NodeId.fresh)
            val propertyEdge = Edge.LabeledProperty(targetNodeId, propertyEdgeData, PropertyId(newPropertyNode.id))
            GraphChanges(addNodes = Set(newPropertyNode), addEdges = Set(propertyEdge))
          }

          sendChanges(addProperty, Right(propertyNode))

        case Left(nodeId)                  =>
          def addProperty(targetNodeId: NodeId): GraphChanges = {
            val propertyEdge = Edge.LabeledProperty(targetNodeId, propertyEdgeData, PropertyId(nodeId))
            GraphChanges(addEdges = Set(propertyEdge))
          }

          sendChanges(addProperty, Left(nodeId))

        case _                             => ()
      }
    }

    def propertyRow(propertyKey: Edge.LabeledProperty, propertyValue: Node)(implicit ctx: Ctx.Owner): VNode = div(
      Styles.flex,
      alignItems.center,
      Components.removablePropertyTag(state, propertyKey, propertyValue),
    )

    description
  }

  def managePropertiesDropdown(state: GlobalState, nodeId: NodeId, prefilledType: Option[NodeData.Type] = Some(defaultType), prefilledKey: String = "", targetNodeIds: Option[Array[NodeId]] = None, descriptionModifier: VDomModifier = VDomModifier.empty, dropdownModifier: VDomModifier = cls := "top left", extendNewProperty: (EdgeData.LabeledProperty, Either[NodeId, Node.Content]) => GraphChanges = (_, _) => GraphChanges.empty)(implicit ctx: Ctx.Owner): VDomModifier = {
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


package wust.webApp

import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.ids.serialize.Circe._
import wust.webApp.state._
import wust.webApp.views._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, UI}

/*
 * Here, the managing of node properties is done.
 * This is done via a simple form.
 * Either just get the form: managePropertiesContent
 * Or get a dropdown of this content: managePropertiesDropdown
 */
object ItemProperties {

  object NodeTypeSelectionHelper {
    import io.circe._
    import io.circe.generic.auto._
    import wust.ids.serialize.Circe._

    implicit val parser: EditStringParser[NodeTypeSelection] = EditImplicits.circe.StringParser[NodeTypeSelection]
    implicit val stringifier: ValueStringifier[NodeTypeSelection] = EditImplicits.circe.Stringifier[NodeTypeSelection]
  }
  import NodeTypeSelectionHelper._

  sealed trait ValueSelection
  object ValueSelection {
    final case class Data(nodeData: NodeData.Content) extends ValueSelection
    final case class Ref(nodeId: NodeId) extends ValueSelection
    case object Placeholder extends ValueSelection
  }

  sealed trait EdgeFactory
  object EdgeFactory {
    final case class Plain(create: (NodeId, NodeId) => Edge) extends EdgeFactory
    final case class Property(prefilledKey: String, prefilledShowOnCard: Boolean, create: (NodeId, String, Boolean, NodeId) => Edge) extends EdgeFactory

    @inline def labeledProperty: Property = labeledProperty("")
    @inline def labeledProperty(key: String): Property = labeledProperty(key, false)
    def labeledProperty(key: String, showOnCard: Boolean): Property = Property(key, showOnCard, (sourceId, key, showOnCard, targetId) => Edge.LabeledProperty(sourceId, EdgeData.LabeledProperty(key, showOnCard), PropertyId(targetId)))
  }
  final case class TypeConfig(prefilledType: Option[NodeTypeSelection] = Some(NodeTypeSelection.Data(NodeData.Markdown.tpe)), hidePrefilledType: Boolean = false, filterRefCompletion: Node => Boolean = _ => true, customOptions: Option[VDomModifier] = None)
  object TypeConfig {
    @inline def default = TypeConfig()
  }

  final case class Names(
    addButton: String = "Add Custom Field"
  )
  object Names {
    @inline def default = Names()
  }

  sealed trait Target
  object Target {
    final case class Node(id: NodeId) extends Target
    final case class Custom(submitAction: (Option[NonEmptyString], NodeId => GraphChanges) => GraphChanges, isAutomation: Rx[Boolean]) extends Target
  }

  def managePropertiesContent(target: Target, config: TypeConfig = TypeConfig.default, edgeFactory: EdgeFactory = EdgeFactory.labeledProperty, names: Names = Names.default, enableCancelButton: Boolean = false)(implicit ctx: Ctx.Owner) = EmitterBuilder.ofNode[Unit] { sink =>

    val prefilledKeyString = edgeFactory match {
      case EdgeFactory.Property(prefilledKey, _, _) => Some(prefilledKey)
      case _                                        => None
    }

    val toggleShowOnCard = edgeFactory match {
      case EdgeFactory.Property(_, prefilledShowOnCard, _) => Some(Var(prefilledShowOnCard))
      case _ => None
    }

    val propertyTypeSelection = Var[Option[NodeTypeSelection]](config.prefilledType)
    val propertyKeyInput = Var[Option[NonEmptyString]](prefilledKeyString.flatMap(NonEmptyString(_)))
    val propertyValueInput = Var[Option[ValueSelection]](None)
    val propertyValueIsPlaceholder = propertyValueInput.map(_.exists { case ValueSelection.Placeholder => true; case _ => false })
    propertyTypeSelection.foreach { selection =>
      propertyValueInput() = None // clear value on each type change...
      if (propertyKeyInput.now.isEmpty) selection.foreach {
        case NodeTypeSelection.Data(NodeData.File.tpe) => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.attachment.key)
        case NodeTypeSelection.Ref                     => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.reference.key)
        case _                                         => ()
      }
    }

    val editModifier = VDomModifier(width := "100%")
    val editableConfig = EditableContent.Config(
      submitMode = EditableContent.SubmitMode.Manual,
      emitter = onInput,
      selectTextOnFocus = false
    )

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Element = null

      val selfOrParentIsAutomationNode = target match {
        case Target.Node(nodeId) => Rx {
          val graph = GlobalState.rawGraph()
          val nodeIdx = graph.idToIdxOrThrow(nodeId)
          graph.selfOrParentIsAutomationTemplate(nodeIdx)
        }
        case Target.Custom(_, isAutomation) => isAutomation
      }

      def createProperty() = {
        handleAddProperty(propertyTypeSelection.now, propertyKeyInput.now, propertyValueInput.now, toggleShowOnCard.fold(false)(_.now))
      }

      form(
        cls := "ui mini form",
        onDomMount.asHtml.foreach { element = _ },

        VDomModifier.ifTrue(prefilledKeyString.isDefined && propertyKeyInput.now.isEmpty)( //do not select key if already specifided
          div(
            cls := "field",
            label("Name"),
            EditableContent.editorRx[NonEmptyString](propertyKeyInput, editableConfig.copy(
              modifier = VDomModifier(
                width := "100%",
                Elements.onEnter.stopPropagation foreach (createProperty()),
                placeholder := "Field Name"
              ),
            )).apply(editModifier)
          )
        ),
        VDomModifier.ifTrue(!config.hidePrefilledType || config.prefilledType.isEmpty)(div( // do not select type if already specifided
          cls := "field",
          label("Type"),
          selfOrParentIsAutomationNode.map { isTemplate =>
            EditableContent.select(
              Some("Select a field type"),
              propertyTypeSelection,
              ("Text", NodeTypeSelection.Data(NodeData.Markdown.tpe)) ::
                ("Number", NodeTypeSelection.Data(NodeData.Decimal.tpe)) ::
                ("File", NodeTypeSelection.Data(NodeData.File.tpe)) ::
                ("Date", NodeTypeSelection.Data(NodeData.Date.tpe)) ::
                ("DateTime", NodeTypeSelection.Data(NodeData.DateTime.tpe)) ::
                ("Duration", NodeTypeSelection.Data(NodeData.Duration.tpe)) ::
                (if (isTemplate) ("Relative Date", NodeTypeSelection.Data(NodeData.RelativeDate.tpe)) :: Nil else Nil) :::
                ("Refer to...", NodeTypeSelection.Ref) ::
                Nil
            ).apply(tabIndex := -1)
          }
        )),
        propertyValueIsPlaceholder.map {
          case false => div(
            cls := "field",
            prefilledKeyString.map(prefilledKey => label(if (prefilledKey.isEmpty) "Value" else prefilledKey)),
            propertyTypeSelection.map(_.flatMap {
              case NodeTypeSelection.Data(propertyType) =>
                EditElementParser.forNodeDataType(propertyType) map { implicit parser =>
                  EditableContent.editorRx[NodeData.Content](
                    propertyValueInput.imap[Option[NodeData.Content]](_.collect { case ValueSelection.Data(data) => data })(_.map(ValueSelection.Data(_))),
                    editableConfig.copy(
                      modifier = VDomModifier(
                        width := "100%",
                        marginTop := "4px",
                        Elements.onEnter.stopPropagation foreach (createProperty())
                      )
                    )
                  ).apply(editModifier)
                }
              case NodeTypeSelection.Ref => Some(
               Components.searchAndSelectNodeApplied(propertyValueInput.imap[Option[NodeId]](_.collect { case ValueSelection.Ref(data) => data })(_.map(ValueSelection.Ref(_))), filter = config.filterRefCompletion).apply(
                  width := "100%",
                  marginTop := "4px",
                )
              )
            }),
          )
          case true => VDomModifier.empty
        },
        config.customOptions getOrElse div(
          cls := "field",
          Styles.flex,
          flexWrap.wrap,
          justifyContent.spaceBetween,
          UI.checkboxEmitter("Mark as Missing", propertyValueIsPlaceholder).map {
            case true  => Some(ValueSelection.Placeholder)
            case false => None
          } --> propertyValueInput,
          toggleShowOnCard.map { toggle =>
            UI.checkbox("Show on Card", toggle)
          },
        ),
        div(
          marginTop := "5px",
          Styles.flex,
          justifyContent.spaceBetween,

          VDomModifier.ifTrue(enableCancelButton)(div(
            cls := "ui button fluid approve",
            "Cancel",
            onClick.stopPropagation.use(()) --> sink
          )),

          div(
            cls := "ui primary fluid button approve",
            Rx {
              VDomModifier.ifTrue((propertyKeyInput().isEmpty && prefilledKeyString.isDefined) || propertyValueInput().isEmpty)(cls := "disabled")
            },
            names.addButton,
            onClick.stopPropagation foreach {
              createProperty()
              target match {
                case Target.Node(nodeId) =>
                  GlobalState.graph.now.nodesById(nodeId).foreach { node =>
                    node.role match {
                      case NodeRole.Task => FeatureState.use(Feature.AddCustomFieldToTask)
                      case _             =>
                    }
                  }
                case _ =>
              }
            }
          ),
        )
      )
    }

    def handleAddProperty(propertyType: Option[NodeTypeSelection], propertyKey: Option[NonEmptyString], propertyValue: Option[ValueSelection], propertyShowOnCard: Boolean)(implicit ctx: Ctx.Owner): Unit = for {
      propertyValue <- propertyValue
      if propertyKey.isDefined || prefilledKeyString.isEmpty
    } {

      def sendChanges(addProperty: NodeId => GraphChanges, extendable: Either[NodeId, Node.Content]) = {
        val changes = target match {
          case Target.Custom(submitAction, _) => submitAction(propertyKey, addProperty)
          case Target.Node(nodeId)            => addProperty(nodeId)
        }

        GlobalState.submitChanges(changes)
      }

      def createEdge(sourceId: NodeId, targetId: NodeId): Edge = edgeFactory match {
        case EdgeFactory.Plain(create)                     => create(sourceId, targetId)
        case EdgeFactory.Property(prefilledKey, _, create) => create(sourceId, propertyKey.fold(prefilledKey)(_.string), propertyShowOnCard, targetId)
      }

      def addDataProperty(data: NodeData.Content): Unit = {
        val propertyNode = Node.Content(NodeId.fresh, data, NodeRole.Neutral)
        def addProperty(targetNodeId: NodeId): GraphChanges = {
          val newPropertyNode = propertyNode.copy(id = NodeId.fresh)
          val propertyEdge = createEdge(targetNodeId, newPropertyNode.id)
          GraphChanges(addNodes = Array(newPropertyNode), addEdges = Array(propertyEdge))
        }

        sendChanges(addProperty, Right(propertyNode))
      }

      propertyValue match {
        case ValueSelection.Placeholder =>
          val data = NodeData.Placeholder(propertyType)
          addDataProperty(data)

        case ValueSelection.Data(data: NodeData.Content) =>
          addDataProperty(data)

        case ValueSelection.Ref(nodeId) =>
          def addProperty(targetNodeId: NodeId): GraphChanges = {
            val propertyEdge = createEdge(targetNodeId, nodeId)
            GraphChanges(addEdges = Array(propertyEdge))
          }

          sendChanges(addProperty, Left(nodeId))

        case _ => ()
      }

      sink.onNext(())
    }

    description
  }

  def managePropertiesDropdown(target: Target, config: TypeConfig = TypeConfig.default, edgeFactory: EdgeFactory = EdgeFactory.labeledProperty, names: Names = Names.default, descriptionModifier: VDomModifier = VDomModifier.empty, dropdownModifier: VDomModifier = cls := "top left", elementModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VDomModifier = {
    val closeDropdown = SinkSourceHandler.publish[Unit]
    UI.dropdownMenu(VDomModifier(
      padding := "5px",
      div(cls := "item", display.none), // dropdown menu needs an item
      elementModifier,
      div(
        managePropertiesContent(target, config, edgeFactory, names).mapResult(_.apply(width := "200px")) --> closeDropdown,
        descriptionModifier
      )
    ), closeDropdown, dropdownModifier = dropdownModifier)
  }

  def managePropertiesInline(target: Target, config: TypeConfig = TypeConfig.default, edgeFactory: EdgeFactory = EdgeFactory.labeledProperty, names: Names = Names.default, descriptionModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[Unit, VDomModifier] = EmitterBuilder.ofModifier { editMode =>
    div(
      padding := "10px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      managePropertiesContent(target, config, edgeFactory, names, enableCancelButton = true) --> editMode,
      descriptionModifier
    )
  }
}

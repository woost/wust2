package wust.webApp

import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Elements, UI}
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.state._
import wust.webApp.views._

/*
 * Here, the managing of node properties is done.
 * This is done via a simple form.
 * Either just get the form: managePropertiesContent
 * Or get a dropdown of this content: managePropertiesDropdown
 */
object ItemProperties {

  sealed trait TypeSelection
  object TypeSelection {
    final case class Data(data: NodeData.Type) extends TypeSelection
    case object Ref extends TypeSelection

    import io.circe._
    import io.circe.generic.auto._
    import wust.ids.serialize.Circe._

    implicit val parser: EditStringParser[TypeSelection] = EditImplicits.circe.StringParser[TypeSelection]
    implicit val stringifier: ValueStringifier[TypeSelection] = EditImplicits.circe.Stringifier[TypeSelection]
  }

  sealed trait ValueSelection
  object ValueSelection {
    final case class Data(nodeData: NodeData.Content) extends ValueSelection
    final case class Ref(nodeId: NodeId) extends ValueSelection
  }

  final case class Config(prefilledType: Option[TypeSelection] = Some(TypeSelection.Data(NodeData.Markdown.tpe)), hidePrefilledType: Boolean = false, prefilledKey: String = "")
  object Config {
    def default = Config()
  }

  sealed trait Target
  object Target {
    final case class Node(id: NodeId) extends Target
    final case class Custom(submitAction: (EdgeData.LabeledProperty, NodeId => GraphChanges) => GraphChanges, isAutomation: Rx[Boolean]) extends Target
  }

  def managePropertiesContent(state: GlobalState, target: Target, config: Config = Config.default, enableCancelButton: Boolean = false)(implicit ctx: Ctx.Owner) = EmitterBuilder.ofNode[Unit] { sink =>

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val propertyTypeSelection = Var[Option[TypeSelection]](config.prefilledType)
    val propertyKeyInput = Var[Option[NonEmptyString]](NonEmptyString(config.prefilledKey))
    val propertyValueInput = Var[Option[ValueSelection]](None)
    propertyTypeSelection.foreach { selection =>
      propertyValueInput() = None// clear value on each type change...
      if (propertyKeyInput.now.isEmpty) selection.foreach {
        case TypeSelection.Data(NodeData.File.tpe) => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.attachment.key)
        case TypeSelection.Ref => propertyKeyInput() = NonEmptyString(EdgeData.LabeledProperty.reference.key)
        case _ => ()
      }
    }

    val editModifier = VDomModifier(width := "100%")
    val editableConfig = EditableContent.Config(
      submitMode = EditableContent.SubmitMode.OnInput,
      selectTextOnFocus = false
    )
    implicit val context:EditContext = EditContext(state)

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Element = null

      val selfOrParentIsAutomationNode = target match {
        case Target.Node(nodeId) => Rx {
          val graph = state.rawGraph()
          val nodeIdx = graph.idToIdxOrThrow(nodeId)
          graph.selfOrParentIsAutomationTemplate(nodeIdx)
        }
        case Target.Custom(_, isAutomation) => isAutomation
      }

      def createProperty() = {
        handleAddProperty(propertyKeyInput.now, propertyValueInput.now)
      }

      form(
        cls := "ui mini form",
        onDomMount.asHtml.foreach { element = _ },

        VDomModifier.ifTrue(propertyKeyInput.now.isEmpty)( //do not select key if already specifided
          div(
            cls := "field",
            label("Name"),
            EditableContent.editorRx[NonEmptyString](propertyKeyInput, editableConfig.copy(
              modifier = VDomModifier(
                width := "100%",
                Elements.onEnter.stopPropagation foreach(createProperty()),
                placeholder := "Field Name"
              ),
            )).apply(editModifier)
          )
        ),
        VDomModifier.ifTrue(!config.hidePrefilledType || config.prefilledType.isEmpty)(div( // do not select type if already specifided
          cls := "field",
          label("Type"),
          selfOrParentIsAutomationNode.map { isTemplate =>
            EditableContent.select[TypeSelection](
              "Select a field type",
              propertyTypeSelection,
              ("Text", TypeSelection.Data(NodeData.Markdown.tpe)) ::
              ("Number", TypeSelection.Data(NodeData.Decimal.tpe)) ::
              ("File", TypeSelection.Data(NodeData.File.tpe)) ::
              ("Date", TypeSelection.Data(NodeData.Date.tpe)) ::
              ("DateTime", TypeSelection.Data(NodeData.DateTime.tpe)) ::
              ("Duration", TypeSelection.Data(NodeData.Duration.tpe)) ::
              (if (isTemplate) ("Relative Date", TypeSelection.Data(NodeData.RelativeDate.tpe)) :: Nil else Nil) :::
              ("Refer to...", TypeSelection.Ref) ::
              Nil
            ).apply(tabIndex := -1)
          }
        )),
        div(
          cls := "field",
          label(if (config.prefilledKey.isEmpty) "Value" else config.prefilledKey),
          propertyTypeSelection.map(_.flatMap {
            case TypeSelection.Data(propertyType) =>
              EditElementParser.forNodeDataType(propertyType) map { implicit parser =>
                EditableContent.editorRx[NodeData.Content](propertyValueInput.imap[Option[NodeData.Content]](_.collect { case ValueSelection.Data(data) => data })(_.map(ValueSelection.Data(_))), editableConfig.copy(
                  modifier = VDomModifier(
                    width := "100%",
                    marginTop := "4px",
                    Elements.onEnter.stopPropagation foreach(createProperty())
                  )
                )).apply(editModifier)
              }
            case TypeSelection.Ref => Some(
              Components.searchAndSelectNodeApplied(state, propertyValueInput.imap[Option[NodeId]](_.collect { case ValueSelection.Ref(data) => data })(_.map(ValueSelection.Ref(_)))).apply(
                width := "100%",
                marginTop := "4px",
              )
            )
          }),
        ),
        div(
          marginTop := "5px",
          Styles.flex,
          justifyContent.spaceBetween,

          VDomModifier.ifTrue(enableCancelButton)(div(
            cls := "ui button fluid approve",
            "Cancel",
            onClick.stopPropagation(()) --> sink
          )),

          div(
            cls := "ui primary fluid button approve",
            Rx {
              VDomModifier.ifTrue(propertyKeyInput().isEmpty || propertyValueInput().isEmpty)(cls := "disabled")
            },
            "Add Custom Field",
            onClick.stopPropagation foreach(createProperty())
          ),
        )
      )
    }

    def handleAddProperty(propertyKey: Option[NonEmptyString], propertyValue: Option[ValueSelection])(implicit ctx: Ctx.Owner): Unit = for {
      propertyKey <- propertyKey
      propertyValue <- propertyValue
    } {

      val propertyEdgeData = EdgeData.LabeledProperty(propertyKey.string)

      def sendChanges(addProperty: NodeId => GraphChanges, extendable: Either[NodeId, Node.Content]) = {
        val changes = target match {
          case Target.Custom(submitAction, _) => submitAction(propertyEdgeData, addProperty)
          case Target.Node(nodeId) => addProperty(nodeId)
        }

        state.eventProcessor.changes.onNext(changes) foreach { _ => clear.onNext (()) }
      }

      propertyValue match {

        case ValueSelection.Data(data: NodeData.Content) =>
          val propertyNode = Node.Content(NodeId.fresh, data, NodeRole.Neutral)
          def addProperty(targetNodeId: NodeId): GraphChanges = {
            val newPropertyNode = propertyNode.copy(id = NodeId.fresh)
            val propertyEdge = Edge.LabeledProperty(targetNodeId, propertyEdgeData, PropertyId(newPropertyNode.id))
            GraphChanges(addNodes = Array(newPropertyNode), addEdges = Array(propertyEdge))
          }

          sendChanges(addProperty, Right(propertyNode))

        case ValueSelection.Ref(nodeId)                  =>
          def addProperty(targetNodeId: NodeId): GraphChanges = {
            val propertyEdge = Edge.LabeledProperty(targetNodeId, propertyEdgeData, PropertyId(nodeId))
            GraphChanges(addEdges = Array(propertyEdge))
          }

          sendChanges(addProperty, Left(nodeId))

        case _                             => ()
      }

      sink.onNext(())
    }

    def propertyRow(propertyKey: Edge.LabeledProperty, propertyValue: Node)(implicit ctx: Ctx.Owner): VNode = div(
      Styles.flex,
      alignItems.center,
      Components.removableNodeCardProperty(state, propertyKey, propertyValue),
    )

    description
  }

  def managePropertiesDropdown(state: GlobalState, target: Target, config: Config = Config.default, descriptionModifier: VDomModifier = VDomModifier.empty, dropdownModifier: VDomModifier = cls := "top left")(implicit ctx: Ctx.Owner): VDomModifier = {
    val closeDropdown = PublishSubject[Unit]
    UI.dropdownMenu(VDomModifier(
      padding := "5px",
      div(cls := "item", display.none), // dropdown menu needs an item
      div(
        managePropertiesContent(state, target, config).mapResult(_.apply(width := "200px")) --> closeDropdown,
        descriptionModifier
      )
    ), closeDropdown, dropdownModifier = dropdownModifier)
  }

  def managePropertiesInline(state: GlobalState, target: Target, config: Config = Config.default, descriptionModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[Unit, VDomModifier] = EmitterBuilder.ofModifier { editMode =>
    div(
      padding := "10px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      managePropertiesContent(state, target, config, enableCancelButton = true) --> editMode,
      descriptionModifier
    )
  }
}


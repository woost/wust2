package wust.webApp.views

import wust.ids._
import wust.graph._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ModalConfig, Ownable}
import wust.webUtil.Elements._
import wust.webApp.state.GlobalState
import wust.webApp.{StringJsOps, Icons}
import wust.css.Styles

import fontAwesome._
import outwatch.dom._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._

import scala.scalajs.js

object ReminderSetup {
  // list all reminders for this nodeId
  def list(nodeId: NodeId)(implicit ctx: Ctx.Owner) = {
    val reminders: Rx[Seq[EdgeData.Remind]] = Rx {
      val g = GlobalState.rawGraph()
      val userId = GlobalState.userId()
      val idx = g.idToIdxOrThrow(nodeId)
      g.remindEdgeIdx.collect(idx) { case edgeIdx if g.edges(edgeIdx).targetId == userId => g.edges(edgeIdx).as[Edge.Remind].data }
    }

    reminders.map { reminders =>
      if (reminders.isEmpty) VDomModifier.empty
      else div(
        b("Active Reminders:"),
        reminders.map { reminder =>
          reminder.toString
        }
      )
    }
  }

  // form to add a new reminder to this nodeid
  def form(nodeId: NodeId)(implicit ctx: Ctx.Owner) = EmitterBuilder.ofModifier[Option[GraphChanges]] { sink =>

    val conditionKindEditor = SinkSourceHandler[ConditionKind](ConditionKind.Date(DateKind.Absolute))

    val currentCondition = SinkSourceHandler[Option[RemindCondition]](None)
    val currentMedium = SinkSourceHandler[RemindMedium](RemindMedium.Email)
    val currentTarget = SinkSourceHandler[RemindTarget](RemindTarget.User)

    val editableConfig = EditableContent.Config(
      submitMode = EditableContent.SubmitMode.Manual,
      emitter = onInput,
      selectTextOnFocus = false,
      autoFocus = false
    )

    def oneLineSelection(icon: VDomModifier, title: VDomModifier, select: VDomModifier, bold: Boolean = true) = div(
      marginBottom := "10px",
      Styles.flex,
      alignItems.center,

      VDomModifier.ifNot(icon == VDomModifier.empty)(div(
        Styles.flexStatic,
        marginRight := "5px",

        icon
      )),

      (if (bold) b else div)(
        Styles.flexStatic,
        marginRight := "20px",

        title
      ),

      select
    )

    val dateEditor: DateKind => VDomModifier = {
      case DateKind.Absolute =>

        oneLineSelection(
          icon = VDomModifier.empty,
          title = "Setup the Date:",
          select =
            EditableContent.editor[DateTimeMilli](editableConfig)
              .editValueOption
              .map(_.map(dt => RemindCondition.AtDate(RemindCondition.Date.Absolute(dt)))).prepend(None) --> currentCondition
        )

      case relative: DateKind.Relative =>
        val propertyName = SinkSourceHandler[String]

        val (multiplier, wording) = relative match {
          case DateKind.Before => (-1, "Before")
          case DateKind.After => (1, "After")
        }

        val datePropertyNames = Rx {
          val graph = GlobalState.rawGraph()
          val propertyData = PropertyData.Single(graph, graph.idToIdxOrThrow(nodeId))
          propertyData.properties.collect { case p if p.values.exists(v => v.node.data.isInstanceOf[NodeData.Date] || v.node.data.isInstanceOf[NodeData.RelativeDate] || v.node.data.isInstanceOf[NodeData.DateTime]) => p.key }
        }

        div(
          oneLineSelection(
            icon = VDomModifier.empty,
            title = "Select Property with a Date:",
            bold = false,
            select = datePropertyNames.map { names =>
              EditableContent.selectEmitter[String](
                Some("Select Property"),
                None,
                names.map(x => x -> x)
              ) --> propertyName,
            },
          ),

          oneLineSelection(
            icon = VDomModifier.empty,
            title = s"$wording that Date:",
            bold = false,
            select =
              EditableContent.editor[DurationMilli](editableConfig).editValueOption.mapResultWithSource[VDomModifier]((result, source) =>
                VDomModifier(
                  div(
                    Styles.flexStatic,
                    marginRight := "5px",

                    source.map[VDomModifier] {
                      case Some(duration) => StringJsOps.durationToString(duration)
                      case None => VDomModifier.empty
                    }
                  ),
                  result
                )
              ).transform(
                _.prepend(Some(DurationMilli(0L)))
                  .combineLatestMap(propertyName) { (dt, name) => dt.map(dt => RemindCondition.AtDate(RemindCondition.Date.Relative(name, DurationMilli(dt * multiplier)))) }
                  .prepend(None)
                  .distinctOnEquals
              ) --> currentCondition
          )
        )
    }

    val stageEditor = VDomModifier(
      "Stage"
    )

    val filledPropertiesEditor = VDomModifier(
      "Filled"
    )

    div(
      padding := "5px",
      cls := "ui mini form",

      oneLineSelection(
        icon = freeRegular.faBell,
        title = "When should we remind you?",
        select = EditableContent.selectAlways(
          None,
          conditionKindEditor,
          ("At fixed Date/Time", ConditionKind.Date(DateKind.Absolute)) ::
          ("Before Date/Time of Property", ConditionKind.Date(DateKind.Before)) ::
          ("After Date/Time of Property", ConditionKind.Date(DateKind.After)) ::
          // ("In Stage", ConditionKind.Stage) ::
          // ("Filled Properties", ConditionKind.FilledProperties) ::
          Nil,
        )
      ),

      div(
        margin := "10px 0 10px 30px",

        conditionKindEditor.distinctOnEquals.map {
          case ConditionKind.Date(kind) => dateEditor(kind)
          case ConditionKind.Stage => stageEditor
          case ConditionKind.FilledProperties => filledPropertiesEditor
        }
      ),

      oneLineSelection(
        icon = freeRegular.faUser,
        title = "Who should be notified?",
        select = EditableContent.selectAlways(
          None,
          currentTarget,
          ("Notify me", RemindTarget.User) ::
          ("Notify the assignee", RemindTarget.Assignee) ::
          Nil,
        )
      ),

      oneLineSelection(
        icon = freeRegular.faEnvelope,
        title = "How should we reach out?",
        select = EditableContent.selectAlways(
          None,
          currentMedium,
          ("Via Email", RemindMedium.Email) ::
          ("Via Push Notification", RemindMedium.Push) ::
          Nil,
        )
      ),

      div(
        Styles.flex,

        button(
          cls := "ui button fluid approve",

          "Cancel",

          onClickDefault.use(None) --> sink
        ),

        button(
          cls := "ui primary fluid button approve",

          "Add Reminder",

          disabled <-- currentCondition.map(_.isEmpty),

          onClickDefault(
            SourceStream.combineLatestMap(currentMedium, currentTarget, currentCondition.collect { case Some(c) => c }) { (m,t,c) =>
              val changes = GraphChanges(addEdges = Array(Edge.Remind(nodeId, EdgeData.Remind(medium = m, target = t, condition = c), GlobalState.userId.now)))
              Some(changes)
            }
          ) --> sink
        ),
      ),
    )
  }

  private sealed trait ConditionKind
  private object ConditionKind {
    case class Date(kind: DateKind) extends ConditionKind
    case object Stage extends ConditionKind
    case object FilledProperties extends ConditionKind

    import io.circe._
    import io.circe.generic.extras.semiauto._
    import wust.ids.serialize.Circe._

    implicit val ConditionKindDecoder: Decoder[ConditionKind] = deriveConfiguredDecoder[ConditionKind]
    implicit val ConditionKindEncoder: Encoder[ConditionKind] = deriveConfiguredEncoder[ConditionKind]

    implicit val parser: EditStringParser[ConditionKind] = EditImplicits.circe.StringParser[ConditionKind]
    implicit val stringifier: ValueStringifier[ConditionKind] = EditImplicits.circe.Stringifier[ConditionKind]
  }

  private sealed trait DateKind
  private object DateKind {
    case object Absolute extends DateKind
    sealed trait Relative extends DateKind
    case object After extends Relative
    case object Before extends Relative

    import io.circe._
    import io.circe.generic.extras.semiauto._
    import wust.ids.serialize.Circe._

    implicit val DateKindDecoder: Decoder[DateKind] = deriveConfiguredDecoder[DateKind]
    implicit val DateKindEncoder: Encoder[DateKind] = deriveConfiguredEncoder[DateKind]

    implicit val parser: EditStringParser[DateKind] = EditImplicits.circe.StringParser[DateKind]
    implicit val stringifier: ValueStringifier[DateKind] = EditImplicits.circe.Stringifier[DateKind]
  }
}

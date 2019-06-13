package wust.cli

import caseapp._
import wust.ids.{NodeId, NodeRole}

@AppName("Woost")
@AppVersion("0.1.0")
@ProgName("woost-cli")
final case class AppOptions(
  @HelpMessage("Profile to load")
  @ValueDescription("profile-name")
  @ExtraName("p")
  profile: Option[String],
  @HelpMessage("Log debug information")
  debug: Boolean = false
)

sealed trait AppCommand
object AppCommand {
  sealed trait Runnable extends AppCommand

  final case class NodeConfig(
    @HelpMessage("Specify NodeRole (default: Task): Task, Message, Project")
    @ValueDescription("node-role")
    @ExtraName("r")
    nodeRole: NodeRole = NodeRole.Task,
    @HelpMessage("Specify ParentId")
    @ValueDescription("cuid")
    @ExtraName("p")
    parentId: Option[NodeId],
  )

  final case class List(
    @Recurse
    nodeConfig: NodeConfig,
  ) extends Runnable

  final case class Add(
    @Recurse
    nodeConfig: NodeConfig,
    @HelpMessage("Pin Node")
    pin: Boolean = false,
    @HelpMessage("Message content")
    @ValueDescription("message")
    @ExtraName("m")
    message: String
  ) extends Runnable

  final case class Configure(
    username: Option[String],
    password: Option[String],
    url: Option[String]
  ) extends Runnable

  case object Help extends AppCommand
}


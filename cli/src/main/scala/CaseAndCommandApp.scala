package wust.cli

import caseapp.core.RemainingArgs
import caseapp.core.app.CommandAppWithPreCommand
import caseapp.core.commandparser.CommandParser
import caseapp.core.help.{CommandsHelp, Help, WithHelp}
import caseapp.core.parser.Parser

abstract class CaseAndCommandApp[Opt : Parser : Help, Cmd : CommandParser : CommandsHelp] extends CommandAppWithPreCommand[Opt, Cmd] {
  private var _opt: Option[Opt] = None

  final override def beforeCommand(opt: Opt, remainingArgs: Seq[String]): Unit = {
    _opt = Some(opt)
    if (remainingArgs.nonEmpty) {
      Console.err.println(s"Found extra arguments: ${remainingArgs.mkString(" ")}")
      sys.exit(255)
    }
  }

  final override def run(cmd: Cmd, args: RemainingArgs): Unit = _opt.foreach(run(_, Some(cmd), args))
  final def run(args: RemainingArgs): Unit = _opt.foreach(run(_, None, args))

  // need to override to handle empty command
  final override def main(args: Array[String]): Unit =
    commandParser.withHelp.detailedParse(args)(beforeCommandParser.withHelp) match {
      case Left(err) =>
        error(err)

      case Right((WithHelp(usage, help, d), dArgs, optCmd)) =>

        if (help)
          helpAsked()

        if (usage)
          usageAsked()

        d.fold(
          error,
          beforeCommand(_, dArgs)
        )

        optCmd.fold(run(RemainingArgs.apply(Nil, Nil))) {
          case Left(err) =>
            error(err)

          case Right((c, WithHelp(commandUsage, commandHelp, t), commandArgs)) =>

            if (commandHelp)
              commandHelpAsked(c)

            if (commandUsage)
              commandUsageAsked(c)

            t.fold(
              error,
              run(_, commandArgs)
            )
        }
    }

  def run(opt: Opt, cmd: Option[Cmd], args: RemainingArgs): Unit
}

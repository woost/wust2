package wust.core

import monix.reactive._
import monix.execution.{Ack, Cancelable, Scheduler}
import wust.api._
import wust.graph._
import wust.ids._
import wust.db.{Data, Db}
import DbConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import scala.concurrent.duration.FiniteDuration

sealed trait ReminderInfo
object ReminderInfo {
  final case class DateTime(timestamp: EpochMilli, medium: RemindMedium, target: RemindTarget) extends ReminderInfo
}

final case class ReminderContext(userDetail: Data.UserDetail, node: Node, reason: String, edge: Data.Edge)

class PollingNotifier(db: Db, emailFlow: AppEmailFlow) {

  private def runStep(interval: FiniteDuration)(implicit ec: ExecutionContext): Future[Ack] = {
    scribe.info("Checking for reminders")

    val currentTime = EpochMilli.now
    val lookBackwardsMillis = interval.toMillis * 2

    // TODO: get actual reminders
    val reminders: Future[Seq[ReminderInfo.DateTime]] = ???

    reminders.flatMap { reminders =>
      val reminderToSend = reminders.filter { reminder =>
        reminder.timestamp > currentTime - lookBackwardsMillis && reminder.timestamp <= currentTime
      }

      // TODO: get actual reminders
      val reminderContexts: Future[Seq[ReminderContext]] = ???

      scribe.info(s"Sending out ${reminderToSend.size} reminders")
      reminderContexts.flatMap { reminderContexts =>
        reminderContexts.foreach(notify)

        db.ctx.transaction { implicit ec =>
          db.edge.delete(reminderContexts.map(_.edge)).map { _ =>
            Ack.Continue
          }
        }
      }
    }.recover { case NonFatal(err) =>
      scribe.warn("Failed to get reminders for polling, will retry.", err)

      Ack.Continue
    }
  }

  private def notify(reminder: ReminderContext)(implicit ec: ExecutionContext): Unit = forClient(reminder.node) match {
    case node: Node.Content => reminder.userDetail.email match {
      case Some(email) => emailFlow.sendReminder(email, node, reminder.reason)
      case None => scribe.warn(s"Cannot send reminder, because user has no email address: $reminder")
    }
    case _ => scribe.warn(s"Reminder on user, this is unexpected, will ignore: $reminder")
  }

  def start(interval: FiniteDuration)(implicit scheduler: Scheduler): Cancelable =
    Observable.intervalAtFixedRate(interval).subscribe(
      _ => runStep(interval),
      err => scribe.error("Error in polling for notifications. Reminder processing is stopped!", err)
    )
}

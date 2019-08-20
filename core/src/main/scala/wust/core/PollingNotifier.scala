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
import scala.concurrent.duration.FiniteDuration

class PollingNotifier(db: Db, emailFlow: AppEmailFlow) {

  private def runStep()(implicit ec: ExecutionContext): Future[Ack] = {
    scribe.info("Checking for reminders")

    // TODO: get actual reminders
    val reminders: Future[Seq[Data.Reminder]] = ???

    reminders.transform {

      case Success(reminders) =>
        scribe.info(s"Sending out ${reminders.size} reminders")
        reminders.foreach { reminder =>
          notify(reminder)
        }

        // TODO: store that we have reminded

        Success(Ack.Continue)

      case Failure(err) =>
        scribe.warn("Failed to get reminders for polling, will retry.", err)

        Success(Ack.Continue)

    }
  }

  private def notify(reminder: Data.Reminder)(implicit ec: ExecutionContext): Unit = forClient(reminder.node) match {
    case node: Node.Content => emailFlow.sendReminder(reminder.email, node)
    case _ => scribe.warn(s"Reminder on user, this is unexpected, will ignore: $reminder")
  }

  def start(interval: FiniteDuration)(implicit scheduler: Scheduler): Cancelable =
    Observable.intervalAtFixedRate(interval).subscribe(
      _ => runStep(),
      err => scribe.error("Error in polling for notifications", err)
    )
}

package wust.webApp.views

import outwatch.dom.dsl._
import wust.facades.hopscotch.{Step, Tour, hopscotch}

import scala.scalajs.js

trait Tutorial {
  val tourId: String
  val tourSteps: js.Array[Step]

  lazy val tour: Tour = new Tour {
    var id = tourId
    var steps = tourSteps
  }

  def waitForNextStep() = waitingForNextStepActivation = true
  var waitingForNextStepActivation = false

  def ifActive(f: Tour => Unit) = {
    hopscotch.getCurrTour.foreach {
      Option(_).foreach { tour =>
        println(s"tour: ${tour.id}[${hopscotch.getCurrStepNum}]")
        tour.id match {
          case `tourId` => f(tour)
        }
      }
    }
  }

  val onDomMountContinue = {
    onDomMount.async.foreach {
      println("continue")
      if (waitingForNextStepActivation) {
        ifActive{ _ =>
          hopscotch.nextStep()
        }
        waitingForNextStepActivation = false
      }
    }
  }

  def startTour(startStep: Step = tour.steps.head) = {
    hopscotch.startTour(tour, tour.steps.indexOf(startStep))
  }

  private def showStep(step: Step) = {
    hopscotch.showStep(tour.steps.indexOf(step))
  }

  def jumpTo(step: Step) = {
    hopscotch.getCurrTour.fold{
      startTour(step)
    }{
      Option(_).foreach{ tour =>
        if (tour.id == tourId) {
          showStep(step)
        } else {
          hopscotch.endTour()
          startTour(step)
        }
      }
    }
  }
}

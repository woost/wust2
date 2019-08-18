package wust.webApp.views

import wust.facades.hopscotch.{Step, hopscotch}

import scala.scalajs.js

object MainTutorial extends Tutorial {
  val tourId = "tour-introduction"
  val tourSteps:js.Array[Step] = js.Array(
    step.projectIntro,
    // step.chooseProjectType,
  )

  // i18n = new hopscotch.I18n {
  //   doneBtn = "Organize Business"
  // }
  // onEnd = {() => defer{hopscotch.startTour(TutorialBusiness.business, 0)}}:js.Function0[Unit]

  object step {
    val projectIntro = new Step {
      title = "Let's get started."
      content = "In Woost, everything starts with a Project. In a Project you can invite other people to collaborate. You can also add different tools, like a Checklist, a Kanban Board or a Chat."
      var target = "tutorial-newprojectbutton"
      var placement = "right"
      showCTAButton = true
      ctaLabel = "Organize a Party!"
      onCTA = { () => hopscotch.endTour(); hopscotch.startTour(TutorialPrivateParty.tour, 0) }: js.Function0[Unit]
      showNextButton = false
    }

    val chooseProjectType = new Step {
      title = "Select your Example"
      content = "Which example do you want to follow?</br></br>"
      var target = "tutorial-newprojectbutton"
      var placement = "right"
      showCTAButton = true
      ctaLabel = "Organize a Party!"
      onCTA = { () => hopscotch.endTour(); hopscotch.startTour(TutorialPrivateParty.tour, 0) }: js.Function0[Unit]
      showNextButton = false
    }

    val clickNewProject = new Step {
      title = "Create New Project"
      content = "Continue by clicking New Project"
      var target = "tutorial-newprojectbutton"
      var placement = "right"
      showNextButton = false
    }

    val selectViews = new Step {
      title = "Configure Views"
      content = "Every Project can contain several Views. Depending on the goal of the project, you can add as many views as you need. Don't worry, if you're not sure right now. You can always change them later.</br></br>For now, let's simply add a checklist.</br></br><b>Click the &quot;Checklist&quot; item to continue.</b>"
      var target = "tutorial-newproject-modal-body"
      var placement = "right"
      showNextButton = false
    }

    val checklistExplain = new Step {
      title = "Checklist"
      content = "Great! Now click the &quot;<b>+</b>&quot; button to create your first project."
      var target = "tutorial-newproject-modal-body"
      var placement = "right"
      showNextButton = false
    }

  }

}

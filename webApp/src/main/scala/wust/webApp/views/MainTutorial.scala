package wust.webApp.views

import wust.facades.hopscotch.{Step, hopscotch}

import scala.scalajs.js

object MainTutorial extends Tutorial {
  val tourId = "tour-introduction"
  val tourSteps:js.Array[Step] = js.Array(
    step.projectIntro,
    step.createProject,
    step.explainViews,
    step.explainTitle,
    step.featureExplorer,
  )

  object step {
    val projectIntro = new Step {
      title = "Let's get started."
      content = "In Woost, everything starts with a Project. In a Project you can invite other people to collaborate. You can also add different tools, like a Checklist, a Kanban Board or a Chat.<br/><br/><b>Continue by creating a Project.</b>"
      var target = "tutorial-newprojectbutton"
      var placement = "right"
      showCTAButton = false
      showNextButton = false
    }

    val createProject = new Step {
      title = "Type Project Name, Select View"
      content = "Frist, give this project a name.<br/>For example <b>Shopping List</b>. Then select the <b>Checklist</b> view and click create."
      var target = "tutorial-modal-inputfield"
      var placement = "right"
      delay = 200
      showCTAButton = false
      showNextButton = false
    }

    val explainViews = new Step {
      title = "Project Views"
      content = "All views will be here. Click tabs to switch. Click + to add or remove views."
      var target = "tutorial-pageheader-viewswitcher"
      var placement = "bottom"
      showCTAButton = false
      showNextButton = true
    }

    val explainTitle = new Step {
      title = "Project Title"
      content = "Click the title to edit or show more options."
      var target = "tutorial-pageheader-title"
      var placement = "bottom"
      showCTAButton = false
      showNextButton = true
    }

    val featureExplorer = new Step {
      title = "Discover new features"
      content = "Based on what you have already tried, new features will be suggested here. Click to expand and see the suggestions."
      var target = "tutorial-feature-explorer"
      var placement = "top"
      showCTAButton = false
      showNextButton = true
    }
  }
}

package wust.webApp.views

import wust.facades.hopscotch.Step

import scala.scalajs.js

object MainTutorial extends Tutorial {
  val tourId = "tour-introduction"
  val tourSteps:js.Array[Step] = js.Array(
    step.unregistredInfo,
    step.projectIntro,
    step.createProject,
    step.explainViews,
    step.explainTitle,
    step.featureExplorer,
  )

  object step {
    val unregistredInfo = new Step {
      title ="You can use Woost without registration."
      content = "Everything you create is private (unless you share it). Whenever you want to access your data from another device, just sign up."
      var target = "tutorial-welcome-authcontrols"
      var placement = "left"
      showCTAButton = false
      showNextButton = true
    }

    val projectIntro = new Step {
      title = "Let's get started."
      content = "In Woost, everything starts with a Project. In a Project you can invite other people to collaborate. You can also add different tools, like a Checklist, a Kanban Board or a Chat.<br/><br/><b>Continue by creating a Project.</b>"
      var target = "tutorial-newprojectbutton"
      var placement = "right"
      showCTAButton = false
      showNextButton = false
    }

    val createProject = new Step {
      title = "Enter Project Name, then select a View"
      content = "First, enter a name for this project.<br/>For example <b>Shopping List</b>. Then select the <b>Checklist</b> view and click <b>Create</b>."
      var target = "tutorial-modal-inputfield"
      var placement = "right"
      delay = 200
      showCTAButton = false
      showNextButton = false
    }

    val explainViews = new Step {
      title = "Project Views"
      content = "Every Project can have several views. Click <b>+</b> to add or remove views. Use the tabs to switch."
      var target = "tutorial-pageheader-viewswitcher"
      var placement = "bottom"
      showCTAButton = false
      showNextButton = true
    }

    val explainTitle = new Step {
      title = "Project Title"
      content = "Click the title to open it in the right sidebar. In fact you can click any task or message to open it in the right sidebar. There you can edit, zoom in, add custom fields, write comments or add subtasks."
      var target = "tutorial-pageheader-title"
      var placement = "bottom"
      showCTAButton = false
      showNextButton = true
    }

    val featureExplorer = new Step {
      title = "Discover new features"
      content = "Based on what you have already tried, new features will be suggested here. Click to expand and see the suggestions.<br/><br/>That's it, we just covered the basics. Now go on and explore Woost the way you like. If you have any questions or ideas, don't hesitate to click <b>Feedback/Support</b> below and open the support chat. We'll be happy to talk to you.<br/><br/>Have a great day!"
      var target = "tutorial-feature-explorer"
      var placement = "top"
      showCTAButton = false
      showNextButton = true
    }
  }
}

package wust.webApp.views

import wust.facades.hopscotch.Step

import scala.scalajs.js

object TutorialPrivateParty extends Tutorial {
  val tourId = "tour-private-party"
  val tourSteps: js.Array[Step] = js.Array(
    new Step {
      title = "Create New Project"
      content = "Continue by clicking New Project"
      var target = "tutorial-newprojectbutton"
      var placement = "right"
      showNextButton = false
    },
    new Step {
      title = "Choose a Project Name"
      content = "Frist, give this project a name.<br/>For example <b>Shopping List</b>"
      // content = "Frist, we give this project a name. In this tutorial we will create a shopping list for the party together. So just write <b>&quot;Shopping List&quot;</b> into the field."
      var target = "tutorial-new-name-input"
      var placement = "right"
    },
    new Step {
      title = "We are creating a Checklist"
      content = "A shopping list is just a checklist with things you need to buy. So just select <b>&quot;Checklist&quot;</b> and then press the <b>&quot;+&quot;</b> button to create the project."
      var target = "tutorial-newproject-modal-body"
      var placement = "right"
      showNextButton = false
    },
    new Step {
      title = "Fill your checklist with items"
      content = "e.g. Beer, Snacks, Paper Plates, Wine, Napkins"
      var target = "tutorial-checklist"
      var placement = "bottom"
    },
    new Step {
      title = "Let's organize your list"
      content = "You can organise them by store or category, whatever you prefer. Add a new item with the name of your category and simply drag and drop the other items in place.<br/> Drinks : Beer, Wine<br/> Food: Snacks<br/> Nonfood: Paper plates, napkins<br/>"
      var target = "tutorial-checklist"
      var placement = "bottom"
    },
    new Step {
      title = "Want to make sure you thought of everything?"
      content = "Add a chat and invite your partner to the project to get feedback and be able to discuss what you really need."
      var target = "tutorial-checklist"
      var placement = "bottom"
      showSkip = true
    }
  )
}

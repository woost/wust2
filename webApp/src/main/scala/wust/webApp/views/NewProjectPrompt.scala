package wust.webApp.views

import flatland._
import org.scalajs.dom
import outwatch._
import outwatch.dsl.{label, _}
import colibri.ext.rx._
import colibri._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{Feature, _}
import wust.webApp._
import wust.webApp.state._
import wust.webApp.views.SharedViewElements._
import wust.webUtil.Elements._
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._

import scala.reflect.ClassTag

object NewProjectPrompt {

  val newProjectText = "New Project"
  def newProjectButton(label: String = newProjectText, focusNewProject: Boolean = true, buttonClass: String = "", extraChanges: NodeId => GraphChanges = _ => GraphChanges.empty): VNode = {
    val selectedViews = Var[Seq[View.Visible]](Seq.empty)
    val triggerSubmit = Subject.publish[Unit]
    val importChanges = Var(Option.empty[GraphChanges.Import])
    def body(implicit ctx: Ctx.Owner) = div(
      color := "#333",
      div("Emojis at the beginning of the name become the Project's icon. For Example ", b(":tomato: Shopping List")),
      viewCheckboxes --> selectedViews,
      // projectImporter.map(Some(_)) --> importChanges,
      div("After creating, you can invite participants by clicking ", button(cls := "ui tiny compact primary button", padding := "0.5em 0.7em", Icons.membersModal), " at the top, next to the project title."),
      div(
        marginTop := "20px",
        Styles.flex,
        justifyContent.flexEnd,
        button(
          "Create",
          cls := "ui violet button",
          onClickDefault.use(()) --> triggerSubmit,
        )
      ),
      MainTutorial.onDomMountContinue,
    )

    def newProject(sub: InputRow.Submission): Unit = {
      MainTutorial.waitForNextStep()
      val newName = if (sub.text.trim.isEmpty) GraphChanges.newProjectName else sub.text
      val nodeId = NodeId.fresh
      val views = if (selectedViews.now.isEmpty) None else Some(selectedViews.now.toList)
      GlobalState.submitChanges(GraphChanges.newProject(nodeId, GlobalState.user.now.id, newName, views) merge sub.changes(nodeId) merge extraChanges(nodeId))

      if (focusNewProject) GlobalState.urlConfig.update(_.focus(Page(nodeId), needsGet = false))

      FeatureState.use(Feature.CreateProject)
      selectedViews.now.foreach (ViewModificationMenu.trackAddViewFeature)

      selectedViews() = Seq.empty
    }

    button(
      cls := s"ui button $buttonClass",
      label,
      onClickNewNamePrompt(
        header = "Create Project",
        body = Ownable { implicit ctx => body },
        placeholder = Placeholder("Name of the Project"),
        showSubmitIcon = false,
        triggerSubmit = triggerSubmit,
        additionalChanges = nodeId => importChanges.now.fold(GraphChanges.empty)(_.resolve(GlobalState.rawGraph.now, nodeId)),
        enableEmojiPicker = true,
        // onCloseOrClickOutside = () => { MainTutorial.endTour() } //TODO
      ).foreach(newProject(_)),
      onClickDefault foreach { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()
        MainTutorial.ifActive{ _ =>
          MainTutorial.jumpBefore(MainTutorial.step.createProject)
          MainTutorial.waitForNextStep()
        }
      },
    )
  }

  def projectImporter(implicit ctx: Ctx.Owner) = EmitterBuilder.ofModifier[GraphChanges.Import] { changesObserver =>
    val showForm = Var(false)

    div(
      showForm.map[VDomModifier] {
        case true  => Importing.inlineConfig --> changesObserver
        case false => button(cls := "ui button mini compact", onClickDefault.foreach(showForm.update(!_)), "Import")
      }
    )
  }

  def viewCheckboxes = multiCheckbox[View.Visible](
    View.selectableNewProjectList,
    view => span(
      marginLeft := "4px",
      ViewSwitcher.viewToTabInfo(view, 0, 0, 0).icon, //TODO: Map[View,Icon] ?
      span(marginLeft := "4px", view.toString)
    ),
  ).mapResult { modifier =>
      VDomModifier(
        width := "100%",
        padding := "20px 10px",
        h3("Select views:", marginBottom := "0px"),
        div("(can be changed later)", fontSize.smaller, color.gray, marginBottom := "15px"),
        modifier,
      )
    }

  def multiCheckbox[T: ClassTag](checkboxes: Array[T], description: T => VDomModifier): EmitterBuilder[Seq[T], VDomModifier] = EmitterBuilder.ofModifier { sink =>
    var checkedState = Array.fill(checkboxes.length)(false)
    val changed = Subject.publish[Unit]

    div(
      Styles.flex,
      flexDirection.column,
      fontSize.larger,
      checkboxes.mapWithIndex { (idx, value) =>
        div(
          marginLeft := "10px",
          marginBottom := "10px",
          label(
            Styles.flex,
            alignItems.center,
            input(
              tpe := "checkbox",
              onChange.checked.foreach { checked =>
                checkedState(idx) = checked
                changed.onNext(())
              },
              onClick.stopPropagation.discard, // fix safari emitting extra click event onChange
              dsl.checked <-- changed.map(_ => checkedState(idx))
            ),
            description(value),
            cursor.pointer,
          )
        )
      },
      emitter(changed).map(_ => checkedState.flatMapWithIndex { (idx, checked) => if (checked) Array(checkboxes(idx)) else Array.empty }: Seq[T]) --> sink,
    )
  }

}

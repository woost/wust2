package wust.webApp.views

import fontAwesome.{IconLookup, freeSolid}
import outwatch.dom.dsl._
import outwatch.dom._
import rx._
import wust.css.Styles
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.outwatchHelpers._

object SplitView {
  private sealed trait Kind
  private object Kind {
    case object Split extends Kind
    case object MaximizeLeft extends Kind
    case object MaximizeRight extends Kind
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {

    val kindHandler = Var[Kind](Kind.Split)

    def viewBox(icon: IconLookup, nextIcon: IconLookup, kind: Kind, nextKind: Kind)(view: => VNode) = {
      def showButton(isLeft: Boolean) = button(cls := "ui primary button", padding := "5px", height := "50px", nextIcon, onClick(Kind.Split) --> kindHandler, position.absolute, top := "200px", if (isLeft) left := "0px" else right := "0px")
      val hideButton = div(paddingRight := "15px", freeSolid.faTimes, cursor.pointer, onClick(nextKind) --> kindHandler, position.absolute, top := "0px", right := "0px")

      div(
        height := "100%",
        position.relative,
        kindHandler.map {
          case Kind.Split => width := "50%"
          case k if k == kind => width := "100%"
          case _ => VDomModifier.empty
        },
        {
          val show = kindHandler.map {
            case Kind.Split => true
            case k if k == kind => true
            case _ => false
          }
          show.map {
            case true  => view(
              Styles.growFull,
              kindHandler.map {
                case Kind.Split => VDomModifier.empty
                case Kind.MaximizeRight => showButton(isLeft = true)
                case Kind.MaximizeLeft => showButton(isLeft = false)
              },
              hideButton
            )
            case false =>  VDomModifier.empty
          }
        },
      )
    }

    div(
      Styles.growFull,
      Styles.flex,
      viewBox(Icons.conversation, Icons.tasks, Kind.MaximizeLeft, Kind.MaximizeRight)(ConversationView(state)),
      viewBox(Icons.tasks, Icons.conversation, Kind.MaximizeRight, Kind.MaximizeLeft)(TasksView(state))
    )
  }
}

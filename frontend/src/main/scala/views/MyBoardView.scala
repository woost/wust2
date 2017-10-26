package wust.frontend.views

import rx._
import wust.frontend._

import org.scalajs.dom.{window, document, console}
import wust.frontend.Color._
import outwatch.dom._
import wust.util.outwatchHelpers._


/// Reusable components that do not depend on a state
// trait MyBoardViewComponents {
// 	import outwatch.dom._
// 	import org.scalajs.dom.raw.{HTMLInputElement}
// 	import MyBoardView.{Entry}


// 	/// Renders a todo entry in the list view
// 	def boardEntry(entry: Entry,
// 				   remEntry : outwatch.Sink[Entry],
// 				   dragStartEvents : outwatch.Sink[Entry],
// 				   changeEvents : outwatch.Sink[Entry]) = {
// 		val inputFocusEvents = createHandler[outwatch.dom.helpers.InputEvent]()
// 		val inputBlurEvents = createHandler[outwatch.dom.helpers.InputEvent]()
// 		val inputChangeEvents = createHandler[outwatch.dom.helpers.InputEvent]()
// 		val editModeEvents = createBoolHandler()
// 		inputFocusEvents.subscribe(ev => {console.log(s"Focus event $ev")})
// 		inputChangeEvents.subscribe(ev => {console.log(s"Change event $ev")})

// 		// - When clicking on the main div, we want to enter edit mode -
// 		val clickEvents = createHandler[org.scalajs.dom.MouseEvent]()
// 		editModeEvents <-- clickEvents.map(_ => true)

// 		/// FIXME: Isnt there a better way to aquire html elements than to give them a unique id?
// 		val inputId = java.util.UUID.randomUUID().toString()
// 		// - When losing focus, we want the input to vanish again
// 		editModeEvents <-- inputBlurEvents.map(_ => false)

// 		// - When clicking on the div, we want the input element to be focused and all its contents selected -
// 		clickEvents.subscribe(ev => {
// 								  document.getElementById(inputId) match {
// 								  	  case htmlTag : HTMLInputElement if document.activeElement != htmlTag => {
// 										  window.setTimeout(() => {
// 																htmlTag.focus()
// 																htmlTag.select()
// 															}, 0)
// 									  }
// 								  	  case _ =>
// 								  }
// 							  })

// 		// - Input changes are propagated outwards, which will cause a re-render of this entire component -
// 		val inputEvents = createStringHandler()
// 		val sentTextEvents = inputChangeEvents.combineLatestWith(inputEvents)((_, text) => text)
// 		changeEvents <-- sentTextEvents.map(Entry(_, entry.uuid))

// 		// - actual html code and event connections -
// 		// FIXME: Isnt there some way to disconnect HTML layout from event chaining?
// 		div(
// 			click --> clickEvents,
// 			draggable := true,
// 			dragstart(entry) --> dragStartEvents,
// 			// either we have a span displaying the contents
// 			span(
// 				hidden <-- editModeEvents,
// 				entry.text),

// 			// or we have an input displaying the contents
// 			input(
// 				id := inputId,
// 				hidden <-- editModeEvents.map(!_).startWith(true),
// 				focus --> inputFocusEvents,
// 				blur --> inputBlurEvents,
// 				change --> inputChangeEvents,
// 				inputString --> inputEvents,
// 				value := entry.text,
// 				entry.text),
// 			button(click(entry) --> remEntry, "X")
// 		)
// 	}


// 	/// returns a clickable entry that spawns an input field to enter a new text
// 	def inputBoardEntry(newEntries: outwatch.Sink[Entry]) = {
// 		val clickedEvents = createBoolHandler()
// 		val inputChangeEvents = createHandler[outwatch.dom.helpers.InputEvent]()
// 		val showNewEntryMessageEvents = clickedEvents.map(ev => {
// 															  console.log(s"Click Event: $ev")
// 															  ev
// 														  })
// 		val showInputEvents = clickedEvents.map(!_).startWith(true)
// 		val inputEvents = createStringHandler()
// 		val sentTextEvents = inputChangeEvents.combineLatestWith(inputEvents)((_, text) => text)
// 		newEntries <-- sentTextEvents.map(Entry(_))
// 		div(
// 			// visible while no input visible
// 			div(
// 				"Add new entry...",
// 				click(true) --> clickedEvents,
// 				hidden <-- showNewEntryMessageEvents
// 			),
// 			// visible only after click
// 			div(
// 				// TODO: re-use input from boardEntry? (with focus/blur & select functionality)
// 				input(
// 					inputString --> inputEvents,
// 					change --> inputChangeEvents
// 				),
// 				hidden <-- showInputEvents
// 			))
// 	}


// 	/// Displays a board with vertically aligned entries
// 	def entryBoardComponent(title : String,
// 							entries : rxscalajs.Observable[Seq[Entry]],
// 							newEntries : outwatch.Sink[Entry],
// 							remEntries : outwatch.Sink[Entry],
// 							entryDragStartEvents : outwatch.Sink[Entry],
// 							entryDropEvents : outwatch.Sink[String],
// 							entryChangeEvents : outwatch.Sink[(String, Entry)]) = {
// 		def buildBoardEntry(entry : Entry) = {
// 			val changeEvents = createHandler[Entry]()
// 			entryChangeEvents <-- changeEvents.map((title, _))
// 			boardEntry(entry, remEntries, entryDragStartEvents, changeEvents)
// 		}
// 		val entriesWrapped = entries.map(_.map(buildBoardEntry(_)) :+ inputBoardEntry(newEntries)).map(l => l.map(li(_)))
// 		val dragOverEvents = createHandler[org.scalajs.dom.DragEvent]()
// 		dragOverEvents.subscribe(e => {
// 									 // console.log("dragOverEvent")
// 									 // TODO: what does this actually do?
// 									 e.preventDefault()
// 									 e.dataTransfer.dropEffect = "move"
// 						   })
// 		val dropEvents = createHandler[org.scalajs.dom.DragEvent]()
// 		entryDropEvents <-- dropEvents.map(_ => title)
// 		div(
// 			h2(title),
// 			ul(
// 				dragover --> dragOverEvents,
// 				drop --> dropEvents,
// 				children <-- entriesWrapped),
// 		)
// 	}


// 	/// Aligns all nodes horizontally via css float left
// 	def horizontalLayout(nodes: VNode*) = {
// 		def floatLeftWrapper(nodes: VNode*) = {
// 			nodes.map(x => div(
// 						  outwatch.dom.Attributes.style := "float: left",
// 						  x))
// 		}

// 		val wrappedNodes = floatLeftWrapper(nodes:_*) :+
// 			div(
// 				outwatch.dom.Attributes.style := "clear: both"
// 			)
// 		div(
// 			wrappedNodes:_*
// 		)
// 	}


// }

object MyBoardView /*extends MyBoardViewComponents*/ {
	// import outwatch.util.Store

	// // - Actions on the view state -
	// sealed trait Action
	// case class AddToBoard(board: String, entry: Entry) extends Action
	// case class RemFromBoard(board: String, entry: Entry) extends Action
	// case class SetDragSource(board: String, entry: Entry) extends Action
	// case class SetDragDest(board: String) extends Action
	// case class UpdateEntry(board: String, newEntry : Entry) extends Action

	// case class Entry(text : String,
	// 				 uuid : java.util.UUID = java.util.UUID.randomUUID())

	// /// State used within this view
	// case class State(text: String,
	// 				 // TODO: entries need an id/position to disambiguate entries with same contents
	// 				 // TODO: We need a mapping from context (e.g. "Work")
	// 				 //       -> board (e.g. "In-Progress") -> entry (e.g. "Do Stuff")
	// 				 entryMap : Map[String, Seq[Entry]],
	// 				 dragSource : Option[(String, Entry)] = None,
	// 				 dragDest : Option[(String, Entry)] = None)
	// val initialState = State("",
	// 						 Map("Todo" -> Seq(Entry("Create Stuff")),
	// 							 "In-Progress" -> Seq(Entry("Create More Stuff"), Entry("Do Things")),
	// 							 "Done" -> Seq.empty))
	// val store = Store(initialState, actionHandler)

	// /// Handler for actions sent to the store which update it
	// private[this] def actionHandler(state: State, action: Action) : State = action match {
	// 	case AddToBoard(board, entry) => state.copy(
	// 		entryMap = state.entryMap + (board -> (state.entryMap.getOrElse(board, Seq.empty) ++ Seq(entry)))
	// 	)
	// 	case RemFromBoard(board, entry) => state.copy(
	// 		entryMap = state.entryMap + (board -> (state.entryMap.getOrElse(board, Seq.empty).filter(_!=entry)))
	// 	)
	// 	case SetDragSource(board, entry) => {
	// 		console.log(s"Setting dragSource to: $board $entry")
	// 		state.copy(dragSource = Some((board, entry)))
	// 	}
	// 	case SetDragDest(board) => {
	// 		console.log(s"Setting dragDest to: $board")
	// 		if(board == state.dragSource.get._1)
	// 			state
	// 		else {
	// 			actionHandler(actionHandler(state, AddToBoard(board, state.dragSource.get._2)),
	// 						  RemFromBoard(state.dragSource.get._1, state.dragSource.get._2))
	// 		}
	// 	}
	// 	case UpdateEntry(board, newEntry) => state.copy(
	// 		entryMap = state.entryMap + (board -> (state.entryMap.getOrElse(board, Seq.empty).map {
	// 												   case entry if entry.uuid == newEntry.uuid => newEntry
	// 												   case other => other
	// 											   }))
	// 	)
	// }


	// /// Main method invoked to render this view
	def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
	// 	import state.persistence

	// 	// we return a raw element, because that is what the main view can handle
	// 	val elem = document.createElement("div")
	// 	import snabbdom._
	// 	patch(elem, render().asProxy)
	// 	elem
      div().render
	}


	// import outwatch.dom._
	// /// construct an entryBoardComponent that is connected to the store
	// private[this] def buildConnectedBoardComponent(name : String) = {
	// 	val newEntries = createHandler[Entry]()
	// 	val remEntries = createHandler[Entry]()
	// 	val entryDragStartEvents = createHandler[Entry]()
	// 	val entryDropEvents = createHandler[String]()
	// 	val entryChangeEvents = createHandler[(String, Entry)]()

	// 	// - connect outgoing streams to store via actions -
	// 	store.sink <-- newEntries.map(AddToBoard(name, _))
	// 	store.sink <-- remEntries.map(RemFromBoard(name, _))
	// 	store.sink <-- entryDragStartEvents.map(SetDragSource(name, _))
	// 	store.sink <-- entryDropEvents.map(_ => SetDragDest(name))
	// 	store.sink <-- entryChangeEvents.map {
	// 		case (board, newEntry) => UpdateEntry(board, newEntry)
	// 	}

	// 	entryBoardComponent(name,
	// 						store.map(_.entryMap.getOrElse(name, Seq.empty)).startWith(Seq.empty),
	// 						newEntries,
	// 						remEntries,
	// 						entryDragStartEvents,
	// 						entryDropEvents,
	// 						entryChangeEvents)
	// }


	// /// Constructs a view with multiple horizontally aligned list views
	// private[this] def kanbanBoard(boards : Seq[String],
	// 							  maybeTitle : Option[String] = None) = {
	// 	val builtBoards = boards.map(buildConnectedBoardComponent(_))

	// 	div(
	// 		h3(maybeTitle match {
	// 				case Some(title) => title
	// 				case None => ""
	// 			}
	// 		),
	// 		horizontalLayout(
	// 			builtBoards:_*
	// 		)
	// 	)
	// }


	// // - actual render template & logic -
	// private[this] def render() = {
	// 	div(
	// 		h1("Outwatch based kanban"),
	// 		kanbanBoard(maybeTitle = Some("Work"), boards = Seq("Todo", "In-Progress", "Done")),
	// 		kanbanBoard(maybeTitle = Some("Finance"), boards = Seq("Unchecked", "Checked")),
	// 	)

	// }
}

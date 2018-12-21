package wust.webApp.views.graphview

import org.scalajs.dom.html.TextArea
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import vectory._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.Placeholders

import scala.collection.breakOut

object SelectedPostMenu {
  def apply(
      pos: Vec2,
      nodeId: NodeId,
      state: GlobalState,
      selectedNodeId: Var[Option[(Vec2, NodeId)]],
      transformRx: Rx[d3v4.Transform]
  )(implicit owner: Ctx.Owner) = {

    val rxPost: Rx[Node.Content] = Rx {
      val graph = state.graph()
      //TODO: getOrElse necessary? Handle post removal.
      //TODO: we are filtering out non-content posts, what about editing them?
      graph.nodesByIdGet(nodeId)
        .collect { case p: Node.Content => p }
        .getOrElse(Node.MarkdownMessage(""))
    }

    val rxTags: Rx[Seq[Node]] = Rx {
      val g = state.graph()
      g.parents(nodeId).map(g.nodesById)(breakOut)
    }

    val transformStyle = Rx {
      val t = transformRx()
      //        val p = rxPost()
      //
      val xOffset = -300 / 2
      val yOffset = 0 //-(p.size.y) / 2
      val x = xOffset + t.applyX(pos.x)
      val y = yOffset + t.applyY(pos.y)
      s"translate(${x}px, ${y}px)"
    }

    val tagList = rxTags.map { tags =>
      div(
        marginBottom := "5px",
        tags.map { tag =>
          removableNodeTag(state, tag, taggedNodeId = rxPost.now.id)
        }
      )
    }

    val editMode = Handler.unsafe[Boolean](false)

    val updatePostHandler = Handler.unsafe[String]
    updatePostHandler.foreach { newContent =>
      val changes =
        GraphChanges.addNode(rxPost.now.copy(data = NodeData.Markdown(newContent)))
      state.eventProcessor.enriched.changes.onNext(changes)

      editMode.onNext(false)
    }

    val insertPostHandler = Handler.unsafe[String]
    insertPostHandler.foreach { content =>
      val newNode = Node.MarkdownMessage(content)

      val changes = GraphChanges(
        addNodes = Set(newNode),
        addEdges = Set(Edge.Parent(newNode.id, rxPost.now.id))
      )
      state.eventProcessor.enriched.changes.onNext(changes)
    }

    val connectPostHandler = Handler.unsafe[String]
    connectPostHandler.foreach { content =>
      val newNode = Node.MarkdownMessage(content)

      val changes = GraphChanges(
        addNodes = Set(newNode),
        addEdges = Set(
          Edge.Label(rxPost.now.id, EdgeData.Label("related"), newNode.id)
        ) ++ state.graph.now
          .parents(rxPost.now.id)
          .map(parentId => Edge.Parent(newNode.id, parentId))
      )
      state.eventProcessor.enriched.changes.onNext(changes)
    }

    val editableTitle = div(
      editMode.map { activated =>
        if (activated) {
          textArea(
            valueWithEnter --> updatePostHandler,
            rxPost.now.data.str,
            onDomMount.map(_.asInstanceOf[TextArea]) foreach(textArea => textArea.select())
          )
        } else {
          div(
            rxPost.map(_.data.str),
            textAlign := "center",
            fontSize := "150%", //post.fontSize,
            Styles.wordWrap,
            display.block,
            margin := "10px",
            onClick(true) --> editMode
          )
        }
      }
    )

    //TODO: wrap in one observable
    div(
      position.absolute,
      onClick foreach(_.stopPropagation()), // prevent click from bubbling to background, TODO: same for dragging
      width := "300px",
      transform <-- transformStyle,
      div(
        rxPost.map(p => actionMenu(p, state, selectedNodeId)(zIndex := -10)), // z-index to overlap shadow
        cls := "shadow",
        editableTitle,
        padding := "3px 5px",
//        border <-- rxPost.map(_.border), //TODO: pass in staticdata
        //        backgroundColor <-- rxPost.map(_.color),
        backgroundColor := "#EEE",
        borderRadius := "5px",
        tagList,
        div(
          cls := "ui form",
          textArea(
            cls := "fluid field",
            rows := 2,
            valueWithEnter --> insertPostHandler,
            Placeholders.newNode,
            marginTop := "20px"
          )
        )
      ),
      // div(
      //   cls := "shadow",
      //   width := "4px",
      //   height := "60px",
      //   margin := s"0 ${(300 - 4) / 2}px",
      //   backgroundColor := "#8F8F8F"
      // ),
      // div(
      //   cls := "shadow",
      //   backgroundColor := "#F8F8F8",
      //   border := "2px solid #DDDDDD",
      //   borderRadius := "5px",
      //   padding := "5px",
      //   textArea(valueWithEnter --> connectPostHandler, Placeholders.newNode)
      // )
    )
  }

  def actionMenu(
      post: Node,
      graphState: GlobalState,
      selectedNodeId: Var[Option[(Vec2, NodeId)]]
  ) = {
    div(
      cls := "shadow",
      position.absolute,
      top := "-55px",
      left := "0px",
      height := "50px",
      width := "300px",
      borderRadius := "5px",
      border := "2px solid #111111",
      backgroundColor := "rgba(0,0,0,0.7)",
      color.white,
      Styles.flex,
      justifyContent.spaceAround,
      alignItems.stretch,
      menuActions.filter(_.showIf(post, graphState)).map { action =>
        div(
          Styles.flex,
          flexDirection.column,
          justifyContent.center,
          flexGrow := 1,
          alignItems.center,
          span(action.name),
          onClick foreach { event =>
            event.stopPropagation()

            println(s"\nMenu ${action.name}: [${post.id}]${post.data}")
            selectedNodeId() = None
            action.action(post, graphState)
            ()
          },
          //TODO: style with css a:hover
          // onmouseover := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "rgba(100,100,100,0.9)" }: js.ThisFunction),
          // onmouseout := ({ (thisNode: HTMLElement, _: Event) => thisNode.style.backgroundColor = "transparent" }: js.ThisFunction),
          // onmousedown := { e: Event => e.preventDefault() }, // disable text selection on menu items
          cursor.pointer
        )
      }
    )
  }

  case class MenuAction(
      name: String,
      action: (Node, GlobalState) => Unit,
      showIf: (Node, GlobalState) => Boolean = (_, _) => true
  )

  val menuActions: List[MenuAction] = List(
    MenuAction("Focus", { (p: Node, state: GlobalState) =>
      state.viewConfig.update(_.focus(Page(p.id)))
    }),
//    MenuAction(
//      "Collapse",
//      action = (p: Post, gs: GraphState) => gs.rxCollapsedNodeIds.update(_ + p.id),
//      showIf = (p: Post, gs: GraphState) => !gs.rxCollapsedNodeIds.now(p.id) && gs.state.rawGraph.now.hasChildren(p.id)
//    ),
//    MenuAction(
//      "Expand",
//      action = (p: Post, gs: GraphState) => gs.rxCollapsedNodeIds.update(_ - p.id),
//      showIf = (p: Post, gs: GraphState) => gs.rxCollapsedNodeIds.now(p.id) && !gs.rxDisplayGraph.now.graph.hasChildren(p.id)
//    ),
    // MenuAction("Split", { (p: Post, s: Simulation[Post]) => logger.info(s"Split: ${p.id}") }),
    MenuAction(
      "Delete", { (p: Node, state: GlobalState) =>
        state.eventProcessor.enriched.changes
          .onNext(
            GraphChanges
              .delete(p.id, state.graph.now.parents(p.id).toSet intersect state.page.now.parentId.toSet)
          )
      }
    ),
    // MenuAction(
    //   "Autopos",
    //   { (p: Post) => p.fixedPos = js.undefined; d3State.simulation.alpha(0.1).restart() },
    //   showIf = (p: Post) => p.fixedPos.isDefined
    // ),
  )
}

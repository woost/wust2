package wust.webApp.views

import cats.effect.IO
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observer
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom.{helpers, _}
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import jquery.JQuerySelection
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import wust.util.StringOps._
import wust.util._
import wust.webApp.BrowserDetect
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._

import scala.scalajs.js
import scala.collection.breakOut

// This file contains woost-related UI helpers.

object Placeholders {
  val newNode = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Components {
  private val implicitUserName = "Unregistered User"


  def displayUserName(user: NodeData.User) = {
    if(user.isImplicit) implicitUserName else user.name
  }

  val htmlNodeData: NodeData => String = {
    case NodeData.Markdown(content)  => markdownString(content)
    case NodeData.PlainText(content) => escapeHtml(content)
    case user: NodeData.User         => s"User: ${ escapeHtml(displayUserName(user)) }"
  }

  def renderNodeData(nodeData: NodeData, maxLength: Option[Int] = None): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
  }

  private val woostPathCurve = "m51.843 221.96c81.204 0-6.6913-63.86 18.402 13.37 25.093 77.23 58.666-26.098-7.029 21.633-65.695 47.73 42.949 47.73-22.746 0-65.695-47.731-32.122 55.597-7.029-21.633 25.093-77.23-62.802-13.37 18.402-13.37z"
  val woostIcon = {
    import svg._
    svg.static(keyValue)(VDomModifier(
      cls := "svg-inline--fa fa-w-14",
      viewBox := "0 0 10 10",
      g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
        path(d := woostPathCurve, fill := "currentColor")
      )
    ))
  }


  val woostLoadingAnimation:VNode = {
    import svg._
    div(
      svg(
        width := "100px", height := "100px", viewBox := "0 0 10 10",
        g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
          path(cls := "woost-loading-animation-logo", d := woostPathCurve, fill := "none", stroke := "#6636b7", strokeLineCap := "round", strokeWidth := "3.5865", pathLength := "100")
        )
      ),
      p("Loading", dsl.color := "rgba(0,0,0,0.4)", textAlign.center)
    )
  }

  def withLoadingAnimation(state: GlobalState)(renderFn: => VDomModifier)(implicit data:Ctx.Data): VDomModifier = {
    if (state.isLoading()) div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, Components.woostLoadingAnimation(cls := "animated-fadein"))
    else renderFn
  }

  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      onClick foreach { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTagDot(state: GlobalState, tag: Node): VNode = {
    span(
      cls := "node tagdot",
      backgroundColor := tagColor(tag.id).toHex,
      title := tag.data.str,
      onClick foreach { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTag(state: GlobalState, tag: Node): VNode = {
    val contentString = trimToMaxLength(tag.data.str, Some(20))
    renderNodeTag(state, tag, contentString)
  }

  def editableNodeTag(state: GlobalState, tag: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = Some(20))(implicit ctx: Ctx.Owner): VNode = {
    renderNodeTag(state, tag, editableNode(state, tag, editMode, submit, maxLength))
  }

  def removableNodeTagCustom(state: GlobalState, tag: Node, action: () => Unit): VNode = {
    nodeTag(state, tag)(
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation foreach {
          action()
        },
      )
    )
  }

  def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId): VNode = {
    removableNodeTagCustom(state, tag, () => {
      // when removing last parent, fall one level lower into the still existing grandparents
      //TODO: move to GraphChange factory
      // val removingLastParent = graph.parents(taggedNodeId).size == 1
      // val addedGrandParents: scala.collection.Set[Edge] =
      //   if (removingLastParent)
      //     graph.parents(tag.id).map(Edge.Parent(taggedNodeId, _))
      //   else
      //     Set.empty
      state.eventProcessor.changes.onNext(
        GraphChanges.delete(taggedNodeId, Set(tag.id))
      )
    })
  }

  def renderNodeCard(node: Node, contentInject: VDomModifier): VNode = {
    div(
      keyed(node.id),
      cls := "node nodecard",
      div(
        cls := "nodecard-content",
        contentInject
      ),
    )
  }
  def nodeCard(node: Node, contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(renderNodeData(node.data, maxLength), contentInject)
    )
  }
  def nodeCardEditable(state: GlobalState, node: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(
        editableNode(state, node, editMode, submit, maxLength),
        contentInject
      )
    ).apply(
      Rx { editMode().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },
    )
  }

  def readDragTarget(elem: dom.html.Element):Option[DragTarget] = {
    readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
  }

  def writeDragTarget(elem: dom.html.Element, dragTarget: => DragTarget): Unit = {
    writePropertyIntoElement(elem, DragItem.targetPropName, dragTarget)
  }

  def readDragPayload(elem: dom.html.Element):Option[DragPayload] = {
    readPropertyFromElement[DragPayload](elem, DragItem.payloadPropName)
  }

  def writeDragPayload(elem: dom.html.Element, dragPayload: => DragPayload): Unit = {
    writePropertyIntoElement(elem, DragItem.payloadPropName, dragPayload)
  }

  def readDragContainer(elem: dom.html.Element):Option[DragContainer] = {
    readPropertyFromElement[DragContainer](elem, DragContainer.propName)
  }

  def writeDragContainer(elem: dom.html.Element, dragContainer: => DragContainer): Unit = {
    writePropertyIntoElement(elem, DragContainer.propName, dragContainer)
  }

  def draggableAs(payload: => DragPayload): VDomModifier = {
    VDomModifier(
      cls := "draggable", // makes this element discoverable for the Draggable library
      onDomMount.asHtml foreach{ elem =>
        writeDragPayload(elem, payload)
      }
    )
  }

  def dragTarget(dragTarget: DragTarget): VDomModifier = {
    onDomMount.asHtml foreach{ elem =>
      writeDragTarget(elem, dragTarget)
    }
  }

  def registerDraggableContainer(state: GlobalState): VDomModifier = {
    VDomModifier(
      //    border := "2px solid blue",
      outline := "none", // hides focus outline
      cls := "draggable-container",
      managedElement.asHtml { elem =>
        state.draggable.addContainer(elem)
        Cancelable { () =>
          state.draggable.removeContainer(elem)
        }
      }
    )
  }

  def registerSortableContainer(state: GlobalState, container: DragContainer): VDomModifier = {
    VDomModifier(
      //          border := "2px solid violet",
      outline := "none", // hides focus outline
      cls := "sortable-container",
      managedElement.asHtml { elem =>
        writeDragContainer(elem, container)
        state.sortable.addContainer(elem)
        Cancelable { () =>
          state.sortable.removeContainer(elem)
        }
      }
    )
  }

  def withOrder(state: GlobalState, page: Page): VDomModifier = {
    val parents = page.parentIds

    state.graph.now.lookup.beforeIdx


    div()
  }

  def editableNodeOnClick(state: GlobalState, node: Node, submit: Observer[GraphChanges])(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val editMode = Var(false)
    editableNode(state, node, editMode, submit)(ctx)(
      onClick.stopPropagation.stopImmediatePropagation foreach {
        if(!editMode.now) {
          editMode() = true
        }
      }
    )
  }


  def editableNode(state: GlobalState, node: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = None)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNodeContent(state, contentNode, editMode, submit, maxLength)
      case _                         => renderNodeData(node.data, maxLength)
    }
  }

  def editableNodeContent(state: GlobalState, node: Node.Content, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int])(
    implicit ctx: Ctx.Owner
  ): VNode = {

    val initialRender: Var[VDomModifier] = Var(renderNodeData(node.data, maxLength))

    def save(contentEditable:HTMLElement): Unit = {
      if(editMode.now) {
        val text = contentEditable.textContent
        val updatedNode = node.copy(data = NodeData.Markdown(text))

        Var.set(
          initialRender -> renderNodeData(updatedNode.data, maxLength),
          editMode -> false
        )

        val changes = GraphChanges.addNode(updatedNode)
        submit.onNext(changes)
      }
    }

    def discardChanges(): Unit = {
      if(editMode.now) {
        editMode() = false
      }
    }

    p( // has different line-height than div and is used for text by markdown
      outline := "none", // hides contenteditable outline
      keyed, // when updates come in, don't disturb current editing session
      Rx {
        if(editMode()) VDomModifier(
          node.data.str, // Markdown source code
          contentEditable := true,
          cls := "enable-text-selection", // fix for macos safari (contenteditable should already be selectable, but safari seems to have troube with interpreting `:not(input):not(textarea):not([contenteditable=true])`)
          whiteSpace.preWrap, // preserve white space in Markdown code
          backgroundColor := "#FFF",
          color := "#000",
          cursor.auto,

          onFocus foreach { e => document.execCommand("selectAll", false, null) },

          if(BrowserDetect.isMobile) VDomModifier(
            onBlur foreach { e => save(e.target.asInstanceOf[HTMLElement]) },
          ) else VDomModifier(
            onEnter foreach { e => save(e.target.asInstanceOf[HTMLElement]) },
            onBlur foreach { discardChanges() },
          ),
          onClick.stopPropagation foreach {} // prevent e.g. selecting node, but only when editing
        ) else initialRender()
      },
      onDomUpdate.asHtml --> inNextAnimationFrame { node =>
        if(editMode.now) node.focus()
      },
    )
  }

  val onIntersectionWithViewport: EmitterBuilder[Boolean, VDomModifier] = onIntersectionWithViewport(ignoreInitial = false)
  def onIntersectionWithViewport(ignoreInitial: Boolean): EmitterBuilder[Boolean, VDomModifier] =
    EmitterBuilder.ofModifier[Boolean] { sink => IO {
      var prevIsIntersecting = ignoreInitial

      VDomModifier(
        managedElement.asHtml { elem =>
          // TODO: does it make sense to only have one intersection observer?
          val observer = new IntersectionObserver(
            { (entry, obs) =>
              val isIntersecting = entry.head.isIntersecting
              if (isIntersecting != prevIsIntersecting) {
                sink.onNext(isIntersecting)
                prevIsIntersecting = isIntersecting
              }
            },
            new IntersectionObserverOptions {
              root = elem.parentElement
              rootMargin = "100px 0px 0px 0px"
              threshold = 0
            }
          )

          observer.observe(elem)

          Cancelable { () =>
            observer.unobserve(elem)
            observer.disconnect()
          }
        }
      )
    }}

  val onInfiniteScrollUp: EmitterBuilder[Int, VDomModifier] =
    EmitterBuilder.ofModifier[Int] { sink => IO {

      var lastHeight = 0.0
      var lastScrollTop = 0.0
      var numSteps = 0

      VDomModifier(
        overflow.auto,
        div(
          div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, woostLoadingAnimation),
          onIntersectionWithViewport(ignoreInitial = false).foreach { isIntersecting =>
            if (isIntersecting) {
              numSteps += 1
              sink.onNext(numSteps)
            }
          }
        ),
        onDomPreUpdate.asHtml.foreach { elem =>
          lastScrollTop = elem.scrollTop
        },
        onDomUpdate.asHtml.foreach { elem =>
          if (elem.scrollHeight > lastHeight) {
            val diff = elem.scrollHeight - lastHeight
            lastHeight = elem.scrollHeight
            elem.scrollTop = diff + lastScrollTop
          }
        }
      )
    }}


  def searchInGraph(graph: Rx[Graph], placeholder: String, filter: Node.Content => Boolean = _ => true): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => IO {
    var elem: JQuerySelection = null
    div(
      keyed,
      cls := "ui category search",
      div(
        cls := "ui icon input",
        input(
          cls := "prompt",
          tpe := "text",
          dsl.placeholder := placeholder,

          onFocus.foreach { _ =>
            elem.search(arg = new SearchOptions {
              `type` = "category"

              cache = false

              source = graph.now.nodes.collect { case node: Node.Content if filter(node) =>
                val parents = graph.now.parentsIdx(graph.now.idToIdx(node.id))
                val cat = if (parents.isEmpty) "-" else trimToMaxLength(parents.map(i => graph.now.nodes(i).str).mkString(","), 18)
                new SearchSourceEntry {
                  title = node.str
                  category = cat
                  data = js.Dynamic.literal(id = node.id.asInstanceOf[js.Any])
                }
              }(breakOut): js.Array[SearchSourceEntry]

              searchFields = js.Array("title")

              onSelect = { (selected, results) =>
                val id = selected.asInstanceOf[js.Dynamic].data.id.asInstanceOf[NodeId]
                sink.onNext(id)
                elem.search("set value", "")
                true
              }: js.Function2[SearchSourceEntry, js.Array[SearchSourceEntry], Boolean]
            })
          }
        ),
        i(cls := "search icon"),
      ),
      div(cls := "results"),

      onDomMount.asJquery.foreach { e =>
        elem = e
      }
    )
  })

  def modalComponent(header: VDomModifier, description: VDomModifier, actions: Option[VDomModifier] = None): VNode = {
    div(
      keyed,
      cls := "ui modal",
      i(cls := "close icon"),
      div(
        cls := "header",
        header
      ),
      div(
        cls := "content",
        div(
          cls := "ui medium",
          div(
            cls := "description",
            description
          )
        ),
        actions.map { actions =>
          div(
            marginLeft := "auto",
            cls := "actions",
            actions
          )
        }
      ),
    )
  }

  def createTaskButton(state: GlobalState, selectedNodes: Rx[Seq[NodeId]] = Var(Nil))(implicit ctx: Ctx.Owner): VDomModifier = IO {
    val selectedParents = Var[List[NodeId]](state.page.now.parentIds.toList)
    var modalElement: JQuerySelection = null
    var searchElement: JQuerySelection = null

    def newMessage(msg: String) = {
      state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(Node.MarkdownTask(msg), selectedParents.now))
    }

    val header = "Create a task"
    val description = VDomModifier(
      div(
        Styles.flex,
        flexDirection.row,
        alignItems.center,
        justifyContent.flexEnd,
        padding := "5px",

        b("Tag: "),
        div(
          paddingLeft := "5px",
          Rx {
            val g = state.graph()
            selectedParents().map(tagId =>
              g.nodesByIdGet(tagId).map { tag =>
                removableNodeTagCustom(state, tag, () => selectedParents.update(list => list.filter(_ != tag.id)))
              }
            )
          }
        ),
        div(
          paddingLeft := "5px",
          searchInGraph(state.graph, placeholder = "Add tag", filter = n => !selectedParents.now.contains(n.id)).foreach { nodeId =>
            selectedParents() = nodeId :: selectedParents.now
          },
        )
      ),

      SharedViewElements.inputField(state, submitAction = newMessage, autoFocus = true)
    )

    div(
      button(
        cls := "ui fluid primary button",
        "Create Task",
        display.block,
        margin := "auto",
        marginTop := "5px",
        onClick.mapTo(modalElement).foreach(_.modal("show"))
      ),

      // TODO: better way to expose element from modal?
      modalComponent(header, description)(onDomMount.asJquery.foreach(modalElement = _))
    )
  }
}

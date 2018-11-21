package wust.webApp.views

import cats.effect.IO
import fomanticui.{SearchOptions, SearchSourceEntry, ToastOptions}
import fontAwesome.{IconLookup, freeRegular}
import monix.execution.Cancelable
import monix.reactive.Observer
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import jquery.JQuerySelection
import wust.css.{Styles, ZIndex}
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

import scala.collection.breakOut
import scala.scalajs.js

// This file contains woost-related UI helpers.

object Placeholders {
  val newNode = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Components {
  private val implicitUserName = "Unregistered User"

  val woostTeamEmailLink = a(href := "mailto:team@woost.space", "team@woost.space", cursor.pointer)

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


  val woostLoadingAnimation: VNode = {
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

  def woostLoadingAnimationWithFadeIn = woostLoadingAnimation(cls := "animated-fadein")

  def customLoadingAnimation(state: GlobalState)(implicit data: Ctx.Data): VNode = {
    div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, cls := "animated-fadein", woostLoadingAnimation)
  }

  def withLoadingAnimation(state: GlobalState)(renderFn: => VDomModifier)(implicit data: Ctx.Data): VDomModifier = {
    if(state.isLoading()) div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, woostLoadingAnimationWithFadeIn)
    else renderFn
  }

  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier, pageOnClick: Boolean): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      if(pageOnClick) onClick foreach { e =>
        state.page() = Page(tag.id)
        e.stopPropagation()
      } else cursor.default,
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTagDot(state: GlobalState, tag: Node): VNode = {
    span(
      cls := "node tagdot",
      backgroundColor := tagColor(tag.id).toHex,
      UI.tooltip := tag.data.str,
      onClick foreach { e =>
        state.page() = Page(tag.id)
        e.stopPropagation()
      },
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTag(state: GlobalState, tag: Node, pageOnClick: Boolean = true): VNode = {
    val contentString = trimToMaxLength(tag.data.str, 20)
    renderNodeTag(state, tag, contentString, pageOnClick)
  }

  def editableNodeTag(state: GlobalState, tag: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = Some(20))(implicit ctx: Ctx.Owner): VNode = {
    renderNodeTag(state, tag, editableNode(state, tag, editMode, submit, maxLength), pageOnClick = false)
  }

  def removableNodeTagCustom(state: GlobalState, tag: Node, action: () => Unit): VNode = {
    nodeTag(state, tag, pageOnClick = false)(
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
  def nodeCardPlain(node: Node, contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(p(StringOps.trimToMaxLength(node.str, maxLength)), contentInject)
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

  def readDragTarget(elem: dom.html.Element): Option[DragTarget] = {
    readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
  }

  def writeDragTarget(elem: dom.html.Element, dragTarget: => DragTarget): Unit = {
    writePropertyIntoElement(elem, DragItem.targetPropName, dragTarget)
  }

  def readDragPayload(elem: dom.html.Element): Option[DragPayload] = {
    readPropertyFromElement[DragPayload](elem, DragItem.payloadPropName)
  }

  def writeDragPayload(elem: dom.html.Element, dragPayload: => DragPayload): Unit = {
    writePropertyIntoElement(elem, DragItem.payloadPropName, dragPayload)
  }

  def readDragContainer(elem: dom.html.Element): Option[DragContainer] = {
    readPropertyFromElement[DragContainer](elem, DragContainer.propName)
  }

  def writeDragContainer(elem: dom.html.Element, dragContainer: => DragContainer): Unit = {
    writePropertyIntoElement(elem, DragContainer.propName, dragContainer)
  }

  def readDraggableDraggedAction(elem: dom.html.Element): Option[() => Unit] = {
    readPropertyFromElement[() => Unit](elem, DragItem.draggedActionPropName)
  }

  def writeDraggableDraggedAction(elem: dom.html.Element, action: => () => Unit): Unit = {
    writePropertyIntoElement(elem, DragItem.draggedActionPropName, action)
  }


  def draggableAs(payload: => DragPayload): VDomModifier = {
    VDomModifier(
      cls := "draggable", // makes this element discoverable for the Draggable library
      onDomMount.asHtml foreach { elem =>
        writeDragPayload(elem, payload)
      }
    )
  }

  def dragTarget(dragTarget: DragTarget): VDomModifier = {
    onDomMount.asHtml foreach { elem =>
      writeDragTarget(elem, dragTarget)
    }
  }

  def onDraggableDragged: EmitterBuilder[Unit, VDomModifier] =
    EmitterBuilder.ofModifier[Unit] { sink =>
      IO {
        VDomModifier(
          onDomMount.asHtml foreach { elem =>
            writeDraggableDraggedAction(elem, () => sink.onNext(Unit))
          }
        )
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

  def searchInGraph(graph: Rx[Graph], placeholder: String, valid: Rx[Boolean] = Var(true), filter: Node => Boolean = _ => true)(implicit ctx: Ctx.Owner): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => IO {
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
              searchOnFocus = true
              minCharacters = 0

              source = graph.now.nodes.collect { case node: Node if filter(node) =>
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


            elem.search("search local", "") // enfoce autocomplete on focus, otherwise it does not work for the initial focus
          },

          valid.map(_.ifFalse[VDomModifier](borderColor := "tomato"))
        ),
        i(cls := "search icon"),
      ),
      div(cls := "results"),

      onDomMount.asJquery.foreach { e =>
        elem = e
      }
    )
  })
}


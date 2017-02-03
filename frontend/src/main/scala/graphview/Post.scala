package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._

class SimPost(val post: Post) extends ExtendedD3Node with SimulationNodeImpl {
  //TODO: delegert!
  def id = post.id
  def title = post.title

  var color = "red"

  var dragClosest: Option[SimPost] = None
  var isClosest = false
  var dropAngle = 0.0
  def dropIndex(n: Int) = {
    val positiveAngle = (dropAngle + 2 * Pi) % (2 * Pi)
    val stepSize = 2 * Pi / n
    val index = (positiveAngle / stepSize).toInt
    index
  }

  def newDraggingPost = {
    val g = new SimPost(post)
    g.x = x
    g.y = y
    g.size = size
    g.centerOffset = centerOffset
    g.color = color
    g
  }
  var draggingPost: Option[SimPost] = None
}

class PostSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
  extends DataSelection[SimPost](container, "div", keyFunction = Some((p: SimPost) => p.id)) {
  import env._
  import PostDrag._

  var postIdToSimPost: Map[AtomId, SimPost] = Map.empty
  def update(posts: Iterable[Post]) {

    val newData = posts.map { p =>
      val sp = new SimPost(p)
      postIdToSimPost.get(sp.id).foreach { old =>
        // preserve position
        sp.x = old.x
        sp.y = old.y
      }
      //TODO: d3-color Facades!
      val parents = graph.parents(p.id)
      val parentColors: Seq[Hcl] = parents.map((p: Post) => baseColor(p.id))
      val colors: Seq[Color] = (if (graph.children(p.id).nonEmpty) baseColor(p.id) else postDefaultColor) +: parentColors
      val labColors = colors.map(d3.lab(_))
      val colorSum = labColors.reduce((c1, c2) => d3.lab(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b))
      val colorCount = labColors.size
      val colorAvg = d3.lab(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
      sp.color = colorAvg.toString()
      sp
    }.toJSArray

    update(newData)
    postIdToSimPost = (newData: js.ArrayOps[SimPost]).by(_.id)
  }

  override def enter(post: Selection[SimPost]) {
    post
      .text((post: SimPost) => post.title)
      .style("background-color", (post: SimPost) => post.color)
      .style("padding", "3px 5px")
      .style("border-radius", "5px")
      .style("border", "1px solid #AAA")
      .style("max-width", "100px")
      .style("position", "absolute")
      .style("cursor", "default")
      .style("pointer-events", "auto") // reenable
      .on("click", { (p: SimPost) =>
        //TODO: click should not trigger drag
        if (focusedPost.isEmpty || focusedPost.get != p)
          focusedPost = Some(p)
        else
          focusedPost = None
        env.postMenuSelection.draw()
      })
      .call(d3.drag[SimPost]()
        .on("start", postDragStarted _)
        .on("drag", postDragged _)
        .on("end", postDragEnded _))

    nodes.each({ (node: HTMLElement, p: SimPost) =>
      //TODO: if this fails, because post is not rendered yet, recalculate it lazyly
      val rect = node.getBoundingClientRect
      p.size = Vec2(rect.width, rect.height)
      p.centerOffset = p.size / -2
      p.radius = p.size.length / 2
      p.collisionRadius = p.radius
    })

  }

  override def drawCall(post: Selection[SimPost]) {
    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
      .style("border", (p: SimPost) => if (p.isClosest) s"5px solid ${dropColors(p.dropIndex(dropActions.size))}" else "1px solid #AAA")
  }
}

class PostMenuSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
  extends DataSelection[SimPost](container, "g", keyFunction = Some((p: SimPost) => p.id)) {
  import env._

  override def enter(menu: Selection[SimPost]) {
    val pie = d3.pie()
      .value(1)
      .padAngle(menuPaddingAngle)

    val arc = d3.arc()
      .innerRadius(menuInnerRadius)
      .outerRadius(menuOuterRadius)
      .cornerRadius(menuCornerRadius)

    val pieData = menuActions.toJSArray
    val ringMenuArc = menu.selectAll("path")
      .data(pie(pieData))
    val ringMenuLabels = menu.selectAll("text")
      .data(pie(pieData))

    ringMenuArc.enter()
      .append("path")
      .attr("d", (d: PieArcDatum[MenuAction]) => arc(d))
      .attr("fill", "rgba(0,0,0,0.7)")
      .style("cursor", "pointer")
      .style("pointer-events", "all")
      .on("click", (d: PieArcDatum[MenuAction]) => focusedPost.foreach(d.data.action(_, simulation)))

    ringMenuLabels.enter()
      .append("text")
      .text((d: PieArcDatum[MenuAction]) => d.data.symbol)
      .attr("text-anchor", "middle")
      .attr("fill", "white")
      .attr("x", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(0))
      .attr("y", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(1))
  }

  override def drawCall(menu: Selection[SimPost]) {
    menu.attr("transform", (p: SimPost) => s"translate(${p.x}, ${p.y})")
  }
}

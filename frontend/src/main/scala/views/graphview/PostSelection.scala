 package wust.frontend.views.graphview

 import org.scalajs.d3v4._
 import org.scalajs.dom.raw.HTMLElement
 import outwatch.dom._
 import outwatch.dom.dsl._
 import wust.frontend.Color._
 import wust.frontend._
 import wust.util.outwatchHelpers._

class PostSelection(graphState: GraphState, d3State: D3State, postDrag: PostDrag, updatedNodeSizes: () => Any) extends DataSelection[SimPost] {
  import postDrag._

  override val tag = "div"
  override def enter(post: Enter[SimPost]): Unit = {
    post.append((simPost: SimPost) => GraphView.postView(simPost.post)(
      content := simPost.content,
      position.absolute,
      pointerEvents.auto, // reenable
      cursor.default
    ).render)
      //TODO: http://bl.ocks.org/couchand/6394506 distinguish between click and doubleclick, https://stackoverflow.com/questions/42330521/distinguishing-click-and-double-click-in-d3-version-4
      //TODO: Doubleclick -> Focus
      .on("click", { (p: SimPost) =>
        DevPrintln(s"\nClicked Post: ${p.id} ${p.content}")
        // Var.set(
        //   VarTuple(rxFocusedSimPost, rxFocusedSimPost.now.map(_.id).setOrToggle(p.id)),
        //   VarTuple(graphState.state.postCreatorMenus, Nil)
        // )
//        rxFocusedSimPost() = rxFocusedSimPost.now.map(_.id).setOrToggle(p.id)
//      graphState.state.inner.focusedPostId() =
        graphState.state.inner.postCreatorMenus() = Nil
      })
      .call(d3.drag[SimPost]()
        .clickDistance(10) // interpret short drags as clicks
        //TODO: click should not trigger drag
        .on("start", { (simPost: SimPost) =>
          // Var.set(
          //   VarTuple(graphState.state.focusedPostId, None),
          //   VarTuple(graphState.state.postCreatorMenus, Nil)
          // )
          graphState.state.inner.focusedPostId() = None
          graphState.state.inner.postCreatorMenus() = Nil
          postDragStarted(simPost)
        })
        .on("drag", postDragged _)
        .on("end", postDragEnded _))
  }

  override def update(post: Selection[SimPost]): Unit = {
    post
      .style("font-size", (post: SimPost) => post.fontSize)
      .style("background-color", (post: SimPost) => post.color)
      .style("border", (p: SimPost) => p.border)
      .style("opacity", (p: SimPost) => p.opacity)
      .text((p: SimPost) => p.content)

    recalculateNodeSizes(post)
  }

  private def recalculateNodeSizes(post: Selection[SimPost]): Unit = {
    post.each({ (node: HTMLElement, p: SimPost) =>
      p.recalculateSize(node, d3State.transform.k)
    })
    updatedNodeSizes()
  }

  private var draw = 0
  override def draw(post: Selection[SimPost]): Unit = {

    // DevOnly {
    //   assert(post.data().forall(_.size.width == 0) || post.data().forall(_.size.width != 0))
    // }
    val onePostHasSizeZero = {
      // every drawcall exactly one different post is checked
      val simPosts = post.data()
      if (simPosts.isEmpty) false
      else simPosts(draw % simPosts.size).size.width == 0

    }
    if (onePostHasSizeZero) {
      // if one post has size zero => all posts have size zero
      // => recalculate all visible sizes
      recalculateNodeSizes(post)
    }

    post
      .style("transform", (p: SimPost) => s"translate(${p.x.get + p.centerOffset.x}px,${p.y.get + p.centerOffset.y}px)")

    draw += 1
  }
}

class PostRadiusSelection(graphState: GraphState, d3State: D3State) extends DataSelection[SimPost] {
  override val tag = "circle"
  override def update(post: Selection[SimPost]): Unit = {
    post
      .attr("stroke", "#444")
      .attr("fill", "transparent")
  }

  override def draw(post: Selection[SimPost]): Unit = {
    post
      .style("transform", (p: SimPost) => s"translate(${p.x.get}px,${p.y.get}px)")
      .attr("r", (p: SimPost) => p.radius)
  }
}

object PostCollisionRadiusSelection extends DataSelection[SimPost] {
  override val tag = "circle"
  override def update(post: Selection[SimPost]): Unit = {
    post
      .attr("stroke", "#666")
      .attr("fill", "transparent")
      .attr("stroke-dasharray", "7,7")
  }

  override def draw(post: Selection[SimPost]): Unit = {
    post
      .style("transform", (p: SimPost) => s"translate(${p.x.get}px,${p.y.get}px)")
      .attr("r", (p: SimPost) => p.radius + Constants.nodePadding / 2)
  }
}

object PostContainmentRadiusSelection extends DataSelection[SimPost] {
  override val tag = "circle"
  override def update(cluster: Selection[SimPost]): Unit = {
    cluster
      .attr("stroke", (simPost: SimPost) => baseColor(simPost.id))
      .attr("fill", "transparent")
      .attr("stroke-dasharray", "10,10")
  }

  override def draw(simPost: Selection[SimPost]): Unit = {
    simPost
      .style("transform", (c: SimPost) => s"translate(${c.x.get}px,${c.y.get}px)")
      .attr("r", (p: SimPost) => p.containmentRadius)
      .style("stroke-width", (p:SimPost) => if(p.collisionRadius != p.containmentRadius) "6px" else "0px")
  }
}

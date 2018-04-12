package wust.webApp.views

import graphview._
import wust.utilWeb.views._

object ChatGraphView extends TiledView {
  override val key = "chat-graph"
  override val displayName = "Chat/Mindmap"
  override protected val views =
    ChatView ::
    new GraphView() ::
    Nil
}

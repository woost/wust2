package wust.ids

case class NodeSchema(
  views: Option[List[NodeView]] = None,
  entities: EntityMap = EntityMap.empty
) {
  def replaceViews(views: Option[List[View.Visible]]): NodeSchema = copy(views = views.map(_.map(NodeView(_))))
  def replaceViews(views: List[View.Visible]): NodeSchema = copy(views = Some(views.map(NodeView(_))))
}
object NodeSchema {
  def empty = NodeSchema()
  def apply(views: Option[List[View.Visible]]): NodeSchema = NodeSchema(views.map(_.map(NodeView(_))))
  def apply(views: List[View.Visible]): NodeSchema = NodeSchema(Some(views.map(NodeView(_))))
  def apply(view: View.Visible): NodeSchema = NodeSchema(Some(NodeView(view) :: Nil))
}

case class NodeView(
  view: View.Visible,
  config: View.Config = View.Config.empty
)


package wust.ids

case class NodeSchema(
  views: ViewMap = ViewMap.empty,
  entities: EntityMap = EntityMap.empty
) {
  // def replaceViews(views: Option[List[View.Custom]]): NodeSchema = ??? //FIXME
  // def replaceViews(views: List[View.Custom]): NodeSchema = ??? //FIXME
}
object NodeSchema {
  @inline def empty = new NodeSchema()
  @inline def apply(ts: (ViewName, ViewConfig)*) = new NodeSchema(views = ts.toMap)
  @inline def fromViews(ts: View.Custom*): NodeSchema = fromViewSeq(ts)
  def fromViewSeq(ts: Seq[View.Custom]): NodeSchema = new NodeSchema(views = ts.map(v => ViewName(v.toString) -> ViewConfig(v)).toMap) //FIXME: to String is not really right
  // def apply(views: Option[List[View.Custom]]): NodeSchema = ??? //FIXME
  // def apply(views: List[View.Custom]): NodeSchema = ??? //FIXME
}


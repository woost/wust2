package wust.ids

case class NodeSchema(
  views: ViewMap = ViewMap.empty,
  entities: EntityMap = EntityMap.empty
) {
}
object NodeSchema {
  @inline def empty = new NodeSchema()
  @inline def apply(ts: (ViewName, ConfiguredView)*) = new NodeSchema(views = ts.toMap)
  @inline def fromViews(ts: View.Custom*): NodeSchema = fromViewSeq(ts)
  def fromViewSeq(ts: Seq[View.Custom]): NodeSchema = new NodeSchema(views = ts.map(v => ViewName(v.toString) -> ConfiguredView(v)).toMap) //FIXME: to String is not really right
}


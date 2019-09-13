package wust.ids

case class Entity(attributes: List[Entity.Attribute])
object Entity {
  case class Attribute(key: String, tpe: NodeTypeSelection)
}

sealed trait NodeTypeSelection
object NodeTypeSelection {
  final case class Data(data: NodeData.Type) extends NodeTypeSelection

  final case class DeepChildrenChain(role: NodeRole) extends NodeTypeSelection

  case object Tasks extends NodeTypeSelection
  case object Messages extends NodeTypeSelection
  case object Notes extends NodeTypeSelection
  case object Files extends NodeTypeSelection
  case object Projects extends NodeTypeSelection
  case object Ref extends NodeTypeSelection
}

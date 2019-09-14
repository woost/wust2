package wust.ids

case class Entity(attributes: List[Entity.Attribute])
object Entity {
  case class Attribute(key: String, selection: NodeTypeSelection)
}

sealed trait NodeTypeSelection
object NodeTypeSelection {

  final case class Data(data: NodeData.Type) extends NodeTypeSelection

  final case class DeepChildrenChain(role: NodeRole) extends NodeTypeSelection

  final case class DirectChildren(role: NodeRole) extends NodeTypeSelection

  case object Ref extends NodeTypeSelection
}

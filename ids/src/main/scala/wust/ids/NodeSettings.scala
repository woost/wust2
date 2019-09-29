package wust.ids

final case class KanbanSettings(hideUncategorized: Boolean = false)

object KanbanSettings {
  val default = KanbanSettings()
}

final case class NodeSettings(
  kanban: Option[KanbanSettings] = None
) {
  def kanbanOrDefault = kanban.getOrElse(KanbanSettings.default)
  def updateKanban(f: KanbanSettings => KanbanSettings): NodeSettings = copy(kanban = Some(f(kanbanOrDefault)))
}

object NodeSettings {
  val default = NodeSettings()
}

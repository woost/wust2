package wust.ids

final case class KanbanSettings(hideUncategorized: Boolean = false)

object KanbanSettings {
  val default = KanbanSettings()
}

final case class TableSettings(showNested: Boolean = false)

object TableSettings {
  val default = TableSettings()
}
final case class FormSettings(title: Option[String] = None)

object FormSettings {
  val default = FormSettings()
}

final case class NodeSettings(
  table: Option[TableSettings] = None,
  kanban: Option[KanbanSettings] = None,
  form: Option[FormSettings] = None
) {
  def tableOrDefault = table.getOrElse(TableSettings.default)
  def updateTable(f: TableSettings => TableSettings): NodeSettings = copy(table = Some(f(tableOrDefault)))
  def kanbanOrDefault = kanban.getOrElse(KanbanSettings.default)
  def updateKanban(f: KanbanSettings => KanbanSettings): NodeSettings = copy(kanban = Some(f(kanbanOrDefault)))
  def formOrDefault = form.getOrElse(FormSettings.default)
  def updateForm(f: FormSettings => FormSettings): NodeSettings = copy(form = Some(f(formOrDefault)))
}

object NodeSettings {
  val default = NodeSettings()
}

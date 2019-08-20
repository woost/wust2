package wust.webApp.views

import wust.graph._

object CsvHelper {

  def tableToCsv(node: Node, propertyGroup: PropertyData.Group): String = {

    val sb = new StringBuilder()

    def addRow(row: Seq[String]): Unit = sb.append(row.mkString(",") + "\n")

    def multiCell(row: Seq[String]): String = row.mkString(";")

    // build the column header line
    //TODO: column names and order should be in order with tableview
    val staticColumns =
      "Name" ::
      "Tags" ::
      "Stages" ::
      "Assignees" ::
      Nil

    val dynamicColumns = propertyGroup.properties.map(_.key)

    val header = staticColumns ++ dynamicColumns

    addRow(header)

    // build the data rows
    propertyGroup.infos.foreach { info =>
      val staticRow =
        info.node.str ::
        multiCell(info.tags.map(_.str)) ::
        multiCell(info.stages.map(_.str)) ::
        multiCell(info.assignedUsers.map(_.str)) ::
        Nil

      val dynamicRow = dynamicColumns.map(key => info.propertyMap.get(key).fold("")(props => multiCell(props.map(_.node.str))))

      val row = staticRow ++ dynamicRow

      addRow(row)
    }

    sb.result
  }
}

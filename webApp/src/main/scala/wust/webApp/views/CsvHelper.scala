package wust.webApp.views

import wust.ids._
import wust.graph._
import kantan.csv._
import kantan.csv.ops._

import wust.util.collection._

// importing and exporting from csv and table. uses kantan.csv library to parse and write csv.

//TODO move to other package...utils?
object CsvHelper {

  //TODO: column names and order should be in order with tableview
  private val staticColumns =
    "Name" ::
    "Tags" ::
    "Stages" ::
    "Assignees" ::
    Nil

  private val multiValueSeparator = ","

  def csvToChanges(csv: String): Either[String, GraphChanges.Import] = {

    def cellSplit(cell: String): Seq[String] = cell.split(multiValueSeparator).filter(_.nonEmpty)

    val config = CsvConfiguration.rfc
    val stringReader = new java.io.StringReader(csv)
    val reader = CsvReader[List[String]](stringReader, config)

    val topLevelNodeIds = Array.newBuilder[NodeId]
    val nodes = Array.newBuilder[Node]
    val edges = Array.newBuilder[Edge]
    val allStages = groupByBuilder[String, ChildId]
    val allTags = groupByBuilder[String, ChildId]

    var header = Option.empty[List[String]]
    while (reader.hasNext) {
      (header, reader.next) match {
        case (Some(header), Right(row)) =>
          if (header.size != row.size)
            return Left("Invalid CSV, row does not correspond to header size")

          val nodeId = NodeId.fresh

          header.zip(row).foreachWithIndex { case (idx, (column, cell)) =>
            // always interpret the first column as the node name
            if (idx == 0) {
              nodes += Node.Content(nodeId, NodeData.Markdown(cell), NodeRole.Task, NodeMeta.default, NodeSchema.empty)
              topLevelNodeIds += nodeId
            } else {
              column match {
                case "Tags" => cellSplit(cell).foreach { tag =>
                  allTags += (tag -> ChildId(nodeId))
                }

                case "Stages" => cellSplit(cell).foreach { stage =>
                  allStages += (stage -> ChildId(nodeId))
                }

                // we cannot import Assignees as users, just text property. Name is handled with idx == 0.
                case propertyName =>
                  if (cell.nonEmpty) {
                    val propertyId = PropertyId(NodeId.fresh)
                    nodes += Node.Content(propertyId, NodeData.Markdown(cell), NodeRole.Neutral, NodeMeta.default, NodeSchema.empty)
                    edges += Edge.LabeledProperty(nodeId, EdgeData.LabeledProperty(key = PropertyKey(propertyName)), propertyId)
                  }
              }
            }

          }

        case (None, Right(row)) =>
          header = Some(row)

        case (_, Left(err)) =>
          return Left(s"Error while parsing CSV: $err")
      }
    }

    allStages.result.foreach { case (stageName, nodeIds) =>
      val stageId = ParentId(NodeId.fresh)
      nodes += Node.Content(stageId, NodeData.Markdown(stageName), NodeRole.Stage, NodeMeta.default, NodeSchema.empty)
      topLevelNodeIds += stageId
      nodeIds.foreach { nodeId =>
        edges += Edge.Child(stageId, nodeId)
      }
    }

    allTags.result.foreach { case (stageName, nodeIds) =>
      val tagId = ParentId(NodeId.fresh)
      nodes += Node.Content(tagId, NodeData.Markdown(stageName), NodeRole.Tag, NodeMeta.default, NodeSchema.empty)
      topLevelNodeIds += tagId
      nodeIds.foreach { nodeId =>
        edges += Edge.Child(tagId, nodeId)
      }
    }

    Right(GraphChanges.Import(
      GraphChanges(addNodes = nodes.result, addEdges = edges.result),
      topLevelNodeIds = topLevelNodeIds.result,
      focusNodeId = None
    ))
  }

  def tableToCsv(node: Node, propertyGroup: PropertyData.Group): String = {

    def multiCell(row: Seq[String]): String = row.mkString(multiValueSeparator)

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

    val config = CsvConfiguration.rfc.withHeader(CsvConfiguration.Header.Explicit(header))
    val stringWriter = new java.io.StringWriter
    val writer = CsvWriter[List[String]](stringWriter, config)

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

      writer.write(row)
    }

    writer.close()
    stringWriter.close()

    stringWriter.toString
  }
}

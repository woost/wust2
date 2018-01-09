package wust.graph
import wust.ids._

sealed trait RestructuringTask {
  val id: Int
  val title: String
  val description: String
}

// Multiple Post RestructuringTask
case object ConnectPosts extends RestructuringTask
{
  val id = 0
  val title = "Connect Posts"
  val description = "Do these posts belong together?"
}
case object MergePosts extends RestructuringTask
{
  val id = 1
  val title = "Merge Posts"
  val description = "Should these post be merged together?"
}

// Single Post RestructuringTask
case object SplitPost extends RestructuringTask
{
  val id = 2
  val title = "Connect Posts"
  val description = "Should this post be split into 2 separate posts?"
}

case object RestructuringTaskGenerator
case object RestructuringTaskChooser {
  def apply(): RestructuringTask = {
    val tasks: List[RestructuringTask] = List(ConnectPosts, MergePosts, SplitPost)
    val taskId = scala.util.Random.nextInt(3)
    tasks(taskId)
  }
}

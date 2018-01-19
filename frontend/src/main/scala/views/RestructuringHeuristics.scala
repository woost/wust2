package wust.frontend.views
import wust.frontend.views.{RestructuringTask} 
import wust.graph.{Post}

object Heuristic {
  type PostHeuristic = (Set[Post], Int) => Set[Post]
  type TaskHeuristic = List[RestructuringTask] => RestructuringTask
}

case object ChooseTaskHeuristic {
  def random(tasks: List[RestructuringTask]): RestructuringTask = {
    tasks(scala.util.Random.nextInt(tasks.size))
  }

  def defaultHeuristic: Heuristic.TaskHeuristic = random
}

case object ChoosePostHeuristic {
  def random(posts: Set[Post], num: Int = 1): Set[Post] = {
    assert(num <= posts.size, "Cannot pick more elements than there are")
    assert(posts.nonEmpty, "Post must not be empty")

    val choice = scala.util.Random.shuffle(posts.toList).take(num).toSet

    assert(choice.nonEmpty, "At least one element must be chosen")

    choice
  }
//  def newest(posts: List[Post], num: Int = 1): List[Post] = {
//    val post = posts.head
//    if(num > 1) List(post) ++ random(posts, num - 1)
//    else List(post)
//  }
//  def gaussTime(posts: List[Post], num: Int = 1): List[Post] = {
//    val post = posts(scala.util.Random.nextInt(posts.size))
//    if(num > 1) List(post) ++ random(posts, num - 1)
//    else List(post)
//  }

  def defaultHeuristic: Heuristic.PostHeuristic = random
}

package backend

import api._, graph._, framework._

object UnauthorizedException extends UserViewableException("unauthorized")
object WrongCredentials extends UserViewableException("wrong credentials")

object Model {
  val users = User("hans") ::
    User("admin") ::
    Nil

  // TODO: the next id will come from the database
  private var currentId: AtomId = 0
  def nextId() = {
    val r = currentId
    currentId += 1
    r
  }
  val post1 = Post(nextId(), "Hallo")
  val post2 = Post(nextId(), "Ballo")
  val responds1 = RespondsTo(nextId(), post2.id, post1.id)
  val post3 = Post(nextId(), "Penos")
  val responds2 = RespondsTo(nextId(), post3.id, responds1.id)
  val post4 = Post(nextId(), "Wost")
  val responds3 = RespondsTo(nextId(), post4.id, responds2.id)
  val container = Post(nextId(), "Container")
  val contains1 = Contains(nextId(), container.id, post1.id)
  val contains2 = Contains(nextId(), container.id, post4.id)

  var graph = Graph(
    Map(post1.id -> post1, post2.id -> post2, post3.id -> post3, post4.id -> post4, container.id -> container),
    Map(responds1.id -> responds1, responds2.id -> responds2, responds3.id -> responds3),
    Map(contains1.id -> contains1, contains2.id -> contains2)
  )

  // TODO: This graph will produce NaNs in the d3 simulation
  // probably because the link force writes a field "index" into both nodes and links and there is a conflict when one edge is a node and a link at the same time.
  // the first NaN occours in linkforce.initialize(): bias[0] becomes NaN
  // var graph = Graph(
  //   Map(0L -> Post(0L, "Hallo"), 1L -> Post(1L, "Ballo"), 4L -> Post(4L, "Penos")),
  //   Map(
  //     5L -> RespondsTo(5L, 4, 2),
  //     14L -> RespondsTo(14L, 0, 1),
  //     13L -> RespondsTo(13L, 4, 1),
  //     2L -> RespondsTo(2L, 1, 0)
  //   ),
  //   Map()
  // )
}

class ApiImpl(userOpt: Option[User], emit: ApiEvent => Unit) extends Api {
  import Model._

  def withUser[T](f: User => T): T = userOpt.map(f).getOrElse {
    throw UnauthorizedException
  }

  def withUser[T](f: => T): T = withUser(_ => f)

  def getPost(id: AtomId): Post = graph.posts(id)
  def deletePost(id: AtomId): Unit = {
    graph = graph.remove(id)
    emit(DeletePost(id))
  }

  def getGraph(): Graph = graph
  def addPost(msg: String): Post = withUser {
    //uns fehlt die id im client
    val post = new Post(nextId(), msg)
    graph = graph.copy(
      posts = graph.posts + (post.id -> post)
    )
    emit(NewPost(post))
    post
  }
  def connect(fromId: AtomId, toId: AtomId): RespondsTo = withUser {
    val existing = graph.respondsTos.values.find(r => r.in == fromId && r.out == toId)
    val edge = existing.getOrElse(RespondsTo(nextId(), fromId, toId))
    graph = graph.copy(
      respondsTos = graph.respondsTos + (edge.id -> edge)
    )
    emit(NewRespondsTo(edge))
    edge
  }
  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  def respond(to: AtomId, msg: String): (Post, RespondsTo) = withUser {
    val post = new Post(nextId(), msg)
    val edge = RespondsTo(nextId(), post.id, to)
    graph = graph.copy(
      posts = graph.posts + (post.id -> post),
      respondsTos = graph.respondsTos + (edge.id -> edge)
    )
    emit(NewPost(post))
    emit(NewRespondsTo(edge))
    (post, edge)
  }
}

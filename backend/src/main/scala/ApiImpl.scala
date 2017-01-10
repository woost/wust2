package backend

import api._, graph._, framework._

object UnauthorizedException extends UserViewableException("unauthorized")
object WrongCredentials extends UserViewableException("wrong credentials")

object Db {
  import io.getquill._

  lazy val ctx = new PostgresJdbcContext[LowerCase]("db")
  import ctx._

  def newPost(title: String): Post = {
    val post = Post(title)
    val q = quote { query[Post].insert(lift(post)).returning(_.id) }
    val returnedId = ctx.run(q)
    post.copy(id = returnedId)
  }

  def newConnects(in: AtomId, out: AtomId): Connects = {
    val connects = Connects(in, out)
    val q = quote { query[Connects].insert(lift(connects)).returning(_.id) }
    val returnedId = ctx.run(q)
    connects.copy(id = returnedId)
  }

  def newContains(parent: AtomId, child: AtomId): Contains = {
    val contains = Contains(parent, child)
    val q = quote { query[Contains].insert(lift(contains)).returning(_.id) }
    val returnedId = ctx.run(q)
    contains.copy(id = returnedId)
  }

  def initGraph(): Graph = {
    println("init graph in db...")
    val post1 = newPost("Hallo")
    val post2 = newPost("Ballo")
    val post3 = newPost("Penos")
    val post4 = newPost("Wost")
    val container = newPost("Container")

    val responds1 = newConnects(post2.id, post1.id)
    val responds2 = newConnects(post3.id, responds1.id)
    val responds3 = newConnects(post4.id, responds2.id)

    val contains1 = newContains(container.id, post1.id)
    val contains2 = newContains(container.id, post4.id)

    println("init done.")

    Graph(
      Map(post1.id -> post1, post2.id -> post2, post3.id -> post3, post4.id -> post4, container.id -> container),
      Map(responds1.id -> responds1, responds2.id -> responds2, responds3.id -> responds3),
      Map(contains1.id -> contains1, contains2.id -> contains2)
    )
  }

  def wholeGraph(): Graph = {
    val posts = ctx.run(query[Post])
    val connects = ctx.run(query[Connects])
    val contains = ctx.run(query[Contains])

    Graph(
      posts.map(p => p.id -> p).toMap,
      connects.map(p => p.id -> p).toMap,
      contains.map(p => p.id -> p).toMap
    )
  }
}

object Model {
  import Db._

  val users = User("hans") ::
    User("admin") ::
    Nil

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
  {
    //TODO init to sql script
    val graph = wholeGraph()
    if (graph.posts.isEmpty) {
      initGraph()
    }
  }
}

class ApiImpl(userOpt: Option[User], emit: ApiEvent => Unit) extends Api {
  import Model._, Db._, ctx._

  def withUser[T](f: User => T): T = userOpt.map(f).getOrElse {
    throw UnauthorizedException
  }

  def withUser[T](f: => T): T = withUser(_ => f)

  def getPost(id: AtomId): Post = {
    // val q = quote { query[Post].filter(_.id == lift(id)) }
    // ctx.run(q)
    ???
  }
  def deletePost(id: AtomId): Unit = {
    //TODO
    emit(DeletePost(id))
  }

  def getGraph(): Graph = wholeGraph
  def addPost(msg: String): Post = withUser {
    //uns fehlt die id im client
    val post = newPost(msg)
    emit(NewPost(post))
    post
  }
  def connect(sourceId: AtomId, targetId: AtomId): Connects = withUser {
    val q = quote { query[Connects].filter(c => c.sourceId == lift(sourceId) && c.targetId == lift(targetId)).take(1) }
    val existing = ctx.run(q)
    val edge = existing.headOption.getOrElse(newConnects(sourceId, targetId))
    emit(NewConnects(edge))
    edge
  }
  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  def respond(to: AtomId, msg: String): (Post, Connects) = withUser {
    val post = newPost(msg)
    val edge = newConnects(post.id, to)
    emit(NewPost(post))
    emit(NewConnects(edge))
    (post, edge)
  }
}

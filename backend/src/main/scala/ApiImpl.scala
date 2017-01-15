package backend

import api._, graph._, framework._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Db {
  import io.getquill._

  lazy val ctx = new PostgresAsyncContext[LowerCase]("db")
  import ctx._

  def newPost(title: String): Future[Post] = {
    val post = Post(title)
    val q = quote { query[Post].insert(lift(post)).returning(_.id) }
    ctx.run(q).map(id => post.copy(id = id))
  }

  def newConnects(in: AtomId, out: AtomId): Future[Connects] = {
    val connects = Connects(in, out)
    val q = quote { query[Connects].insert(lift(connects)).returning(_.id) }
    ctx.run(q).map(id => connects.copy(id = id))
  }

  def newContains(parent: AtomId, child: AtomId): Future[Contains] = {
    val contains = Contains(parent, child)
    val q = quote { query[Contains].insert(lift(contains)).returning(_.id) }
    ctx.run(q).map(id => contains.copy(id = id))
  }

  def initGraph(): Future[Graph] = {
    println("init graph in db...")
    for (post1 <- newPost("Hallo");
         post2 <- newPost("Ballo");
         post3 <- newPost("Penos");
         post4 <- newPost("Wost");
         container <- newPost("Container");
         responds1 <- newConnects(post2.id, post1.id);
         responds2 <- newConnects(post3.id, responds1.id);
         responds3 <- newConnects(post4.id, responds2.id);
         contains1 <- newContains(container.id, post1.id);
         contains2 <- newContains(container.id, post4.id)) yield {

        println("init done.")

          Graph(
          Map(post1.id -> post1, post2.id -> post2, post3.id -> post3, post4.id -> post4, container.id -> container),
          Map(responds1.id -> responds1, responds2.id -> responds2, responds3.id -> responds3),
          Map(contains1.id -> contains1, contains2.id -> contains2)
        )
    }
  }

  def wholeGraph(): Future[Graph] = {
    for (posts <- ctx.run(query[Post]);
          connects <- ctx.run(query[Connects]);
          contains <- ctx.run(query[Contains])) yield {

      Graph(
        posts.map(p => p.id -> p).toMap,
        connects.map(p => p.id -> p).toMap,
        contains.map(p => p.id -> p).toMap
      )
    }
  }
}

object Model {
  import Db._

  val users = User("hans") ::
    User("admin") ::
    Nil

  {
    //TODO init to sql script
    for (graph <- wholeGraph() if graph.posts.isEmpty) {
      initGraph()
    }
  }
}

class ApiImpl(userOpt: Option[User], emit: ApiEvent => Unit) extends Api {
  import Model._, Db._, ctx._

  def withUser[T](f: User => Future[T]): Future[T] = userOpt.map(f).getOrElse {
    Future.failed(UserError(Unauthorized))
  }

  def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)

  def getPost(id: AtomId): Future[Option[Post]] = {
    val q = quote { query[Post].filter(_.id == lift(id)).take(1) }
    ctx.run(q).map(_.headOption)
  }

  def deletePost(id: AtomId): Future[Boolean] = withUser {
    val q = quote { query[Post].filter(_.id == lift(id)).delete }
    for (_ <- ctx.run(q)) yield {
      emit(DeletePost(id))
      true
    }
  }

  def getGraph(): Future[Graph] = wholeGraph()

  def addPost(msg: String): Future[Post] = withUser {
    for (post <- newPost(msg)) yield {
      emit(NewPost(post))
      post
    }
  }

  def connect(sourceId: AtomId, targetId: AtomId): Future[Option[Connects]] = withUser {
    val connects = Connects(sourceId, targetId)
    val q = quote {
      query[Connects].insert(lift(connects)).returning(x => x.id)
    }
    val newId = ctx.run(q).map(Some(_)).recover {
      case e: /*GenericDatabaseException*/Exception => None
    }
    for (idOpt <- newId) yield idOpt.map { id =>
      val edge = connects.copy(id = id)
      emit(NewConnects(edge))
      edge
    }
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  //TODO: felix no option
  def respond(to: AtomId, msg: String): Future[Option[(Post, Connects)]] = withUser {
    //TODO do in one request, does currently not handle errors
    for(post <- newPost(msg);
        edgeOpt <- connect(post.id, to)) yield edgeOpt.map { edge =>
      emit(NewPost(post))
      emit(NewConnects(edge))
      (post, edge)
    }
  }
}


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

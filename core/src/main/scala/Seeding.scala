package wust.backend

import wust.graph.{Connection, Post, User}
import wust.ids._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaj.http.HttpResponse

object Constants {
  //TODO
  val githubId = PostId("wust-github")
  val issueTagId = PostId("github-issue")
  val commentTagId = PostId("github-comment")
  val gitterId = PostId("wust-gitter")
}
object GitHubImporter {
  import github4s.Github
  import github4s.Github._
  import github4s.GithubResponses.GHResult
  import github4s.free.domain.{Comment, Issue, User => GHUser}
  import github4s.jvm.Implicits._

  private val gitAccessToken = sys.env.get("WUST_GITHUB_TOKEN")

  def urlExtractor(url: String): (String, String, Option[Int]) = {

    println("Extracting url")
    val _url = url.stripLineEnd.stripMargin.trim.
      stripPrefix("https://").
      stripPrefix("http://").
      stripPrefix("github.com/").
      stripSuffix("/")
    val numEndPattern = "issues/[0-9]+$".r
    val issueNumGiven = numEndPattern.findFirstIn(_url).getOrElse("/").split("/")
    val issueNum = if (issueNumGiven.isEmpty) None else Some(issueNumGiven(1).toInt)
    val urlData = _url.stripSuffix("/").stripSuffix((if(issueNum.isDefined) issueNum.get.toString else "")).stripSuffix("/").stripSuffix("/issues").split("/")

    println(s"url ${url}")
    println(s"urlData: owner = ${urlData(0)}, repo = ${urlData(1)}, issue number = ${issueNum}")
    assert(urlData.size == 2, "Could not extract url")

    (urlData(0), urlData(1), issueNum)
  }

  def getIssues(owner: String, repo: String, issueNumber: Option[Int] = None, user: User): Future[(Set[Post], Set[Connection])] = {

    val emptyResult = (Set.empty[Post], Set.empty[Connection])

    // TODO: Deduplication
    def getSingleIssue(number: Int): Future[List[Issue]] =
      Github(gitAccessToken).issues.getIssue(owner, repo, number)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => List(result)
          case _ => {
            println("Error getting Issue")
            List.empty[Issue]
          }
        }

    def getIssueList: Future[List[Issue]] =
      Github(gitAccessToken).issues.listIssues(owner, repo)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => result
          case _ => {
            println("Error getting List of Issues")
            List.empty[Issue]
          }
        }

    val issueList = issueNumber match {
      case Some(number) => getSingleIssue(number)
      case _ => getIssueList
    }

    val issueWithComments: Future[List[(Issue, List[Comment])]] = {
      issueList.flatMap( inner => Future.sequence(inner.map( issue => {
        val issueComments: Future[(Issue, List[Comment])] =
          Github(gitAccessToken).issues.listComments(owner, repo, issue.number)
            .execFuture[HttpResponse[String]]().map( response => response match {
            case Right(GHResult(result, _, _)) => (issue, result)
            case _ => {
              println("Error getting Issues")
              (issue, List.empty[Comment])
            }
          })
        issueComments
      })))
    }

    val postAndConnection: Future[Set[(Set[Post], Set[Connection])]] = {
      issueWithComments.map(_.map( issueData => {
        val issue = issueData._1
        val commentsList = issueData._2

        //TODO what about this userid?
        val userId = UserId(issue.user match {
          case None => ???
          case Some(githubUser: GHUser) => githubUser.id.toString
        }) //TODO: create this user
        val tempUserId = user.id

        // Ensure posts
        val _github = Post(Constants.githubId, "wust-github", tempUserId)
        val _issue = Post(Constants.issueTagId, "wust-github-issue", tempUserId)
        val _comment = Post(Constants.commentTagId, "wust-github-comment", tempUserId)
        val _github_issue = Connection(_issue.id, Label.parent, _github.id)
        val _github_comment = Connection(_comment.id, Label.parent, _github.id)

        // TODO: delete transitive containments of comments in issue

        // Issue posts and connections
        // implicit def StringToEpochMilli(s:String):EpochMilli = EpochMilli.from(s)
        // val issueTitle = Post(PostId(issue.number.toString), s"#${issue.number} ${issue.title}", tempUserId, issue.created_at, issue.updated_at)
        // val issueTitle = Post(PostId(issue.number.toString), s"#${issue.number} ${issue.title}", tempUserId, issue.created_at, issue.updated_at)
        val issueIdZeros = (9 - issue.number.toString.length - 1) // temp. workaround for cuid order
        val issueTitle = Post(PostId("1" + "0"*issueIdZeros + issue.number.toString), s"#${issue.number} ${issue.title}", tempUserId, issue.created_at, issue.updated_at)

        val titleIssueTag = Connection(issueTitle.id, Label.parent, _issue.id)

        val desc = if(issue.body.nonEmpty) {
          val issueDesc = Post(PostId(issue.id.toString), issue.body, tempUserId, issue.created_at, issue.updated_at)
          val conn = Connection(issueDesc.id, Label("describes"), issueTitle.id)
          val cont = Connection(issueDesc.id, Label.parent, issueTitle.id)
          val comm = Connection(issueDesc.id, Label.parent, _comment.id)
          (Set(issueDesc), Set(conn, cont, comm))
        } else {
          (Set.empty[Post], Set.empty[Connection])
        }

        val issuePosts = Set[Post](_github, _issue, _comment, issueTitle) ++ desc._1
        val issueConn = Set[Connection](_github_issue, _github_comment, titleIssueTag) ++ desc._2

        // Comments
        val comments: List[(Post, Set[Connection])] = commentsList.map(comment => {
          val cpost = Post(PostId(comment.id.toString), comment.body, tempUserId, comment.created_at, comment.updated_at)
          val cconn = Set(Connection(cpost.id, Label.parent, issueTitle.id), Connection(cpost.id, Label.parent, _comment.id))
          (cpost, cconn)
        })

        val (commentPosts, commentConnections) = comments.unzip

        val posts = issuePosts ++ commentPosts.toSet
        val connections = issueConn ++ commentConnections.toSet.flatten
        (posts, connections)

      }).toSet)
    }

    postAndConnection.map(zipped => {
      val (posts, conns) = zipped.unzip
      (posts.flatten, conns.flatten)
    })

  }

}

object GitterImporter {
  import scala.collection.JavaConverters._
  import com.amatkivskiy.gitter.sdk.sync.client.SyncGitterApiClient

  private val gitterAccessToken = sys.env.getOrElse("WUST_GITTER_TOKEN", "")

  def getRoomMessages(url: String, user: User): Future[(Set[Post], Set[Connection])] = {
    val _uri = url.stripLineEnd.stripMargin.trim.
      stripPrefix("https://").
      stripPrefix("http://").
      stripPrefix("gitter.im/").
      takeWhile(_ != '?').
      stripSuffix("/")
    val tempUserId = user.id
    val client: SyncGitterApiClient = new SyncGitterApiClient.Builder().withAccountToken(gitterAccessToken).build()

    // Ensure gitter post
    val _gitter = Post(Constants.gitterId, "wust-gitter", tempUserId)

    val discussion = Post(PostId.fresh, _uri, tempUserId)
    val discussionTag = Connection(discussion.id, Label.parent, _gitter.id)
    val postsAndConnection = for {
      roomId <- Future { client.getRoomIdByUri(_uri).id }
      roomMessages <- Future { client.getRoomMessages(roomId).asScala.toList }
    } yield {
      roomMessages.map { message =>
        //TODO what about this userid?
        val post = Post(PostId.fresh, message.text, tempUserId)
        val conn = Connection(post.id, Label.parent, discussion.id)
        (Set(post), Set(conn))
      }.toSet
    }

    postsAndConnection.map(zipped => {
      val (posts, conns) = zipped.unzip
      (posts.flatten + _gitter + discussion, conns.flatten + discussionTag)
    })
  }
}

package wust.github

import wust.graph._
import wust.ids._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaj.http.HttpResponse

import github4s.Github
import github4s.Github._
import github4s.GithubResponses.GHResult
import github4s.free.domain.{Comment, Issue, User => GHUser}
import github4s.jvm.Implicits._
import com.redis._

object GitHubImporter {

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
    val urlData = _url.stripSuffix("/").stripSuffix(if (issueNum.isDefined) issueNum.get.toString else "").stripSuffix("/").stripSuffix("/issues").split("/")

    println(s"url data: owner = ${urlData(0)}, repo = ${urlData(1)}, issue number = $issueNum")
    assert(urlData.size == 2, "Could not extract url")

    (urlData(0), urlData(1), issueNum)
  }

  def getIssues(redis: RedisClient, owner: String, repo: String, issueNumber: Option[Int] = None, gitAccessToken: Option[String]): Future[(Set[Node], Set[Edge])] = {

    val emptyResult = (Set.empty[Node], Set.empty[Edge])

    // TODO: Deduplication
    def getSingleIssue(number: Int): Future[List[Issue]] =
      Github(gitAccessToken).issues.getIssue(owner, repo, number)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => List(result)
          case _ =>
            println("Error getting Issue")
            List.empty[Issue]
        }

    def getIssueList: Future[List[Issue]] =
      Github(gitAccessToken).issues.listIssues(owner, repo)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => result
          case _ =>
            println("Error getting List of Issues")
            List.empty[Issue]
        }

    val issueList = issueNumber match {
      case Some(number) => getSingleIssue(number)
      case _ => getIssueList
    }

    val issueWithComments: Future[List[(Issue, List[Comment])]] = {
      issueList.flatMap( inner => Future.sequence(inner.map( issue => {
        val issueComments: Future[(Issue, List[Comment])] =
          Github(gitAccessToken).issues.listComments(owner, repo, issue.number)
            .execFuture[HttpResponse[String]]().map {
            case Right(GHResult(result, _, _)) => (issue, result)
            case _ =>
              println("Error getting Issues")
              (issue, List.empty[Comment])
          }
        issueComments
      })))
    }

    /**
      * Create property posts and connection for each issue
      * For each post / comment:
      *   Check if user exist. if not -> generate uuid for new implicit user. else get wust user
      * Save data in redis
      * Get tokens for implicit users from backend
      *
      */
    val postAndConnection: Future[Set[(Set[Node], Set[Edge])]] = {
      issueWithComments.map(_.map( issueData => {
        val issue = issueData._1
        val commentsList = issueData._2

        // Create property connection



        // Get wust user from github id or create new user
        val githubUserOfIssue = issue.user
        val wustUserOfIssue = githubUserOfIssue match {
          case None => "unknown".asInstanceOf[UserId]
          case Some(githubUser: GHUser) => PersistAdapter.getWustUser(githubUser.id).getOrElse(UserId.fresh)
        }

      //TODO what about this userid?
      val userId = (issue.user match {
        case None => ???
        case Some(githubUser: GHUser) => githubUser.id.toString
      }).asInstanceOf[UserId] //TODO: create this user
      // val tempUserId = user.id
      val tempUserId = wustUserOfIssue


        // Ensure posts
//        val _github = Post(Constants.githubId, PostData.Text("wust-github"), tempUserId)
//        val _issue = Post(Constants.issueTagId, PostData.Text("wust-github-issue"), tempUserId)
//        val _comment = Post(Constants.commentTagId, PostData.Text("wust-github-comment"), tempUserId)
//        val _github_issue = Connection(_issue.id, ConnectionData.Parent, _github.id)
//        val _github_comment = Connection(_comment.id, ConnectionData.Parent, _github.id)
        val _github = Node.Content(Constants.githubId, NodeData.PlainText("wust-github"))
        val _issue = Node.Content(Constants.issueTagId, NodeData.PlainText("wust-github-issue"))
        val _comment = Node.Content(Constants.commentTagId, NodeData.PlainText("wust-github-comment"))
        val _github_issue = Edge.Parent(_issue.id, _github.id)
        val _github_comment = Edge.Parent(_comment.id, _github.id)

        // TODO: delete transitive containments of comments in issue

        // Issue posts and connections
        implicit def StringToEpochMilli(s: String): EpochMilli = EpochMilli.from(s)
        // val issueTitle = Post(NodeId(issue.number.toString), s"#${issue.number} ${issue.title}", tempUserId, issue.created_at, issue.updated_at)
        // val issueTitle = Post(NodeId(issue.number.toString), s"#${issue.number} ${issue.title}", tempUserId, issue.created_at, issue.updated_at)
        val issueIdZeros = (9 - issue.number.toString.length - 1) // temp. workaround for cuid order

//        val issueTitle = Post(NodeId("1" + augmentString("0")*issueIdZeros + issue.number.toString), PostData.PlainText(s"#${issue.number} ${issue.title}"), tempUserId, issue.created_at, issue.updated_at)
        val issueId:NodeId = ??? //NodeId("1" + augmentString("0")*issueIdZeros + issue.number.toString)
        val issueTitle = Node.Content(issueId, NodeData.PlainText(s"#${issue.number} ${issue.title}"))

        val titleIssueTag = Edge.Parent(issueTitle.id, _issue.id)

        val desc = if(issue.body.nonEmpty) {
//          val issueDesc = Post(NodeId(issue.id.toString), PostData.Markdown(issue.body), tempUserId, issue.created_at, issue.updated_at)
          val issueId: NodeId = ??? //NodeId(issue.id.toString)
          val issueDesc = Node.Content(issueId, NodeData.Markdown(issue.body))
          val conn = Edge.Label(issueDesc.id, EdgeData.Label("describes"), issueTitle.id)
          val cont = Edge.Parent(issueDesc.id, issueTitle.id)
          val comm = Edge.Parent(issueDesc.id, _comment.id)
          (Set(issueDesc), Set(conn, cont, comm))
        } else {
          (Set.empty[Node], Set.empty[Edge])
        }

        val issuePosts = Set[Node](_github, _issue, _comment, issueTitle) ++ desc._1
        val issueConn = Set[Edge](_github_issue, _github_comment, titleIssueTag) ++ desc._2

        // Comments
        val comments: List[(Node.Content, Set[Edge.Parent])] = commentsList.map(comment => {
//          val cpost = Post(NodeId(comment.id.toString), PostData.Markdown(comment.body), tempUserId, comment.created_at, comment.updated_at)
          val commentId: NodeId = ??? //NodeId(commend.id.toString)
          val cpost = Node.Content(commentId, NodeData.Markdown(comment.body))
          val cconn = Set(Edge.Parent(cpost.id, issueTitle.id), Edge.Parent(cpost.id, _comment.id))
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

package wust.cli

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import wust.api.AuthUser
import wust.graph._
import wust.ids._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Commander {
  private val defaultWoostUrl = "https://core.app.woost.space/api"

  def apply(opts: AppOptions, cmd: AppCommand.Runnable, remainingArgs: Seq[String]): Unit = cmd match {
    case cmd: AppCommand.Configure =>
      val config = readConfig(username = cmd.username, password = cmd.password, url = cmd.url)
      storeConfig(opts, config)

    case cmd: AppCommand.Add => withClient(opts) { (client, user) => implicit ec =>
      val node = Node.Content(NodeData.Markdown(cmd.message), cmd.nodeConfig.nodeRole)
      client.api.changeGraph(
        GraphChanges(
          addNodes = Set(node),
          addEdges = Set[Edge]()
            ++ (if (cmd.pin) Set(Edge.Pinned(node.id, user.id)) else Set.empty)
            ++ cmd.nodeConfig.parentId.map(parentId => Edge.Child(ParentId(parentId), ChildId(node.id)))

        ).withAuthor(user.id)
      )
    }

    case cmd: AppCommand.List => withClient(opts) { (client, user) => implicit ec =>
      client.api.getNodeList(cmd.nodeConfig.parentId, Some(cmd.nodeConfig.nodeRole)).map { nodes =>
        val header = s"${cmd.nodeConfig.nodeRole}s:"

        val descriptions = nodes.map { node =>
          s"  - ${node.content} (${node.id.toBase58})"
        }

        val lines = header :: descriptions

        println(lines.mkString("\n"))
      }
    }
  }

  private def withConfig(opts: AppOptions)(f: Config => Unit): Unit = Config.load(opts.profile) match {
    case Right(config) => f(config)
    case Left(errors) =>
      scribe.warn(s"Cannot load config file: ${errors.toList.mkString(", ")}")
      val config = readConfig()
      storeConfig(opts, config)
      f(config)
  }

  private def withClient[U](opts: AppOptions)(f: (WustHttpClient, AuthUser.Persisted) => ExecutionContext => Future[U]): Unit = withConfig(opts)(withClient(_)(f))
  private def withClient[U](config: Config)(f: (WustHttpClient, AuthUser.Persisted) => ExecutionContext => Future[U]): Unit = {
    val appConfig = ConfigFactory.load()
    implicit val system = ActorSystem("system", appConfig)
    import system.dispatcher

    var headers: List[(String, String)] = Nil //TODO: better
    val client = WustHttpClient(config.url, headers)

    client.auth.loginReturnToken(config.username, Password(config.password)).onComplete {
      case Success(Some(auth)) =>
        headers = ("Authorization", auth.token.string) :: Nil
        f(client, auth.user)(system.dispatcher).onComplete {
          case Success(_) =>
            system.terminate()
          case Failure(error) =>
            scribe.error(s"Request failed: ${error.getMessage}")
            system.terminate()
        }
      case _ =>
        scribe.error(s"Failed to login")
        system.terminate()
    }
  }

  private def readConfig(username: Option[String] = None, password: Option[String] = None, url: Option[String] = None): Config = {
    val console = System.console()
    scribe.info("Configuring Woost...")

    val newUsername = username.getOrElse {
      print("Username: ")
      console.readLine()
    }

    val newPassword = password.getOrElse {
      print("Password: ")
      new String(console.readPassword())
    }

    val newUrl = url.getOrElse(defaultWoostUrl)

    Config(username = newUsername, password = newPassword, url = newUrl)
  }

  private def storeConfig(opts: AppOptions, config: Config): Unit = {
    Config.store(config, opts.profile) match {
      case Right(()) => ()
      case Left(error) => scribe.error(s"Cannot store config: $error")
    }
  }
}

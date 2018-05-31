package wust.github
import com.redis._

import wust.ids.UserId

object DBConstants {
  val wustPrefix = "wu_"
  val githubPrefix = "gh_"
  val githubUserId = "githubUserId"
  val wustUserId = "wustUserId"
  val githubToken = "githubToken"
  val wustToken = "wustToken"
}

object PersistAdapter {
  val client = new RedisClient("localhost", 6379)

  // If one has wust data and want to set / get data
  def addWustToken(wustUserId: UserId, wustToken: String): Option[Long] = {
    client.hset1(s"${DBConstants.wustPrefix}$wustUserId", DBConstants.wustToken, wustToken)
  }
  def getWustToken(wustUserId: String): Option[String] = {
    client.hget[String](s"${DBConstants.wustPrefix}$wustUserId", DBConstants.wustToken)
  }
  def addGithubUser(wustUserId: UserId, githubUserId: Int): Option[Long] = {
    client.hset1(s"${DBConstants.wustPrefix}$wustUserId", DBConstants.githubUserId, s"${DBConstants.githubPrefix}$githubUserId")
  }
  def getGithubUser(wustUserId: UserId): Option[String] = {
    val rawUser = client.hget[String](s"${DBConstants.wustPrefix}$wustUserId", DBConstants.githubUserId)
    rawUser.map(_.drop(DBConstants.githubPrefix.length))
  }

  // If one has github data and want to set / get data
  def addGithubToken(githubUserId: Int, githubToken: String): Option[Long] = {
    client.hset1(s"${DBConstants.githubPrefix}$githubUserId", DBConstants.githubToken, githubToken)
  }
  def getGithubToken(githubUserId: Int): Option[String] = {
    client.hget[String](s"${DBConstants.githubPrefix}$githubUserId", DBConstants.githubToken)
  }
  def addWustUser(githubUserId: Int, wustUserId: UserId): Option[Long] = {
    client.hset1(s"${DBConstants.githubPrefix}$githubUserId", DBConstants.wustUserId, s"${DBConstants.wustPrefix}$wustUserId")
  }
  def getWustUser(githubUserId: Int): Option[UserId] = {
    val rawUser = client.hget[String](s"${DBConstants.githubPrefix}$githubUserId", DBConstants.wustUserId)
//    rawUser.map(userStr => UserId(userStr.drop(DBConstants.wustPrefix.length)))
    rawUser.map(userStr => (userStr.drop(DBConstants.wustPrefix.length)).asInstanceOf[UserId])
  }

}

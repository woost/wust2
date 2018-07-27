object PersistenceAdapter {

  type SlackUserId = String
  type SlackChannelId = String

  def getOrCreateWustUser(slackUser: SlackUserId): (UserId, Authentication.Token) = ???

  def getChannelNode(channel: SlackChannelId): NodeId = ???

  def method(): = ???
}

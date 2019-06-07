package wust.android

class MessageReceiver extends FirebaseMessagingService {
  override def onMessageReceived(remoteMessage: RemoteMessage): Unit = {
    super.onMessageReceived(remoteMessage)
    println(s"received message: $remoteMessage")
  }
}

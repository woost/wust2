package wust.android

import com.google.firebase.messaging.{FirebaseMessagingService, RemoteMessage}

class MessageReceiver extends FirebaseMessagingService {
  override def onMessageReceived(remoteMessage: RemoteMessage): Unit = {
    super.onMessageReceived(remoteMessage)
    println(s"received message: $remoteMessage")
  }
}

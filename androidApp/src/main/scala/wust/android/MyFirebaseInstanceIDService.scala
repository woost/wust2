package wust.android

import com.google.firebase.iid.{FirebaseInstanceId, FirebaseInstanceIdService}

class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
  // https://medium.com/@nileshsingh/how-to-add-push-notification-capability-to-your-android-app-a3cac745e56e
  override def onTokenRefresh(): Unit = {
    super.onTokenRefresh()
    val token = FirebaseInstanceId.getInstance().getToken()
    println(s"got token: $token")
  }
}

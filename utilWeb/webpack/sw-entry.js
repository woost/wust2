self.addEventListener('push', function(e) {
  var body;

  console.log("Push notification", e);
  if (e.data) {
    body = e.data.text();
  } else {
    body = 'Push message no payload';
  }

  var options = {
    body: body,
    icon: 'icon.ico',
    vibrate: [100, 50, 100],
    data: {
      dateOfArrival: Date.now(),
      primaryKey: 1
    },
    tag: "push",
    renotify: true
    // actions: [
    //   {action: 'explore', title: 'Explore this new world',
    //     icon: 'images/checkmark.png'},
    //   {action: 'close', title: 'I don't want any of this',
    //     icon: 'images/xmark.png'},
    // ]
  };
  e.waitUntil(
    self.registration.showNotification('Push Notification', options)
  );
});

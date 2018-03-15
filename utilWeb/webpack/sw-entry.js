console.log("ServiceWorker starting!");
const baseUrl = location.protocol + '//core.' + location.hostname + ':' + location.port + '/api';
self.registration.pushManager.getSubscription().then(subscription => {
    sendSubscriptionToBackend(subscription);
});


Promise.prototype.flatMap = function(fun) {
    let self = this;
    return new Promise((resolve, reject) => {
        self.then(result => fun(result).then(resolve, reject), reject);
    });
};
Promise.prototype.map = function(fun) {
    let self = this;
    return new Promise((resolve, reject) => {
        self.then(result => resolve(fun(result)), reject);
    });
};
function requestPromise(request) {
    return new Promise((resolve, reject) => {
        request.onsuccess = e => resolve(request.result);
        request.onerror = e => reject(request.error);
    });
}

var _db;
function db() {
    if (!_db) {
        let openreq = indexedDB.open('woost', 1);
        openreq.onupgradeneeded = () => {
            openreq.result.createObjectStore('auth');
        };
        _db = requestPromise(openreq);
    }
    return _db;
}

function currentAuth() {
    return db().flatMap(db => {
        let transaction = db.transaction(["auth"], "readwrite");
        let store = transaction.objectStore("auth");
        return requestPromise(store.get(0));
    });
}

self.addEventListener('push', e => {
    console.log("ServiceWorker received push notification", e);
    if(Notification.permission != "granted") {
        console.log("ServiceWorker received but notifications are not granted, ignoring");
        return;
    }

    let body = e.data ? e.data.text() : 'Push message no payload';
    let options = {
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

self.addEventListener('pushsubscriptionchange', e => {
    // resubscribe and send new subscription to backend
    self.registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: "" //TODO
    }).then(subscription => {
        sendSubscriptionToBackend(subscription);
    });
});

function sendSubscriptionToBackend(subscription) {
    if (!subscription) {
        return;
    }

    currentAuth().then(currentAuth => {
        fetch(baseUrl + '/Api/subscribeWebPush', {
            method: 'POST',
            body: JSON.stringify({ subscription: subscription }),
            headers: {
                'Authorization': currentAuth
            }
        });
    });
}

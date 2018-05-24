// workbox.precaching.precacheAndRoute(self.__precacheManifest || []);

// cache google fonts
// workbox.routing.registerRoute(
//   new RegExp('https://fonts.(?:googleapis|gstatic).com/(.*)'),
//   workbox.strategies.cacheFirst({
//     cacheName: 'googleapis',
//     plugins: [
//       new workbox.expiration.Plugin({
//         maxEntries: 30,
//       }),
//     ],
//   }),
// );

/////////////////////////////////////////

warn = s => console.warn("[SW] " + s);
log = s => console.log("[SW] " + s);
error = s => console.error("[SW] " + s);

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
Promise.empty = new Promise((resolve, reject) => resolve()); //should this be an error? :)
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

function getPublicKey() {
    return fetch(baseUrl + '/Push/getPublicKey', { method: 'POST', body: '{}' }); // TODO: use empty payload?
}

function sendSubscriptionToBackend(subscription) {
    if (!subscription || !subscription.getKey) { // current subscription can be null if user did not enable it
        return Promise.empty;
    }

    let key = subscription.getKey('p256dh');
    let auth = subscription.getKey('auth');
    if (!key || !auth) {
        warn("Subscription without key/auth, ignoring.", key, auth);
        return Promise.empty;
    }

    let subscriptionObj = {
        endpointUrl: subscription.endpoint,
        p256dh: btoa(String.fromCharCode.apply(null, new Uint8Array(key))),
        auth: btoa(String.fromCharCode.apply(null, new Uint8Array(auth)))
    };

    log("Sending subscription to backend", subscriptionObj);
    return currentAuth().flatMap(currentAuth => fetch(baseUrl + '/Push/subscribeWebPush', {
        method: 'POST',
        body: JSON.stringify({ subscription: subscriptionObj }),
        headers: {
            'Authorization': currentAuth
        }
    }));
}

// startup
log("ServiceWorker starting!");
const baseUrl = location.protocol + '//core.' + location.hostname + ':' + location.port + '/api';
self.registration.pushManager.getSubscription().then(sendSubscriptionToBackend, t => warn("Failed to get subscription", t));

// to test push renewal, trigger event manually:
// setTimeout(() => self.dispatchEvent(new ExtendableEvent("pushsubscriptionchange")), 3000);

self.addEventListener('push', e => {
    log("ServiceWorker received push notification", e);
    if(Notification.permission != "granted") {
        log("ServiceWorker received but notifications are not granted, ignoring");
        return;
    }

    e.waitUntil(
        self.clients.matchAll({type: 'window'}).flatMap(clients => {
            if (clients.length > 0) {
                log("ServiceWorker has active clients, ignoring push notification");
                return Promise.empty;
            } else {
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
                    renotify: true,
                    actions: [
                      {action: 'explore', title: 'Explore this new world'}
                    ]
                };

                return self.registration.showNotification('Push Notification', options)
            }
        })
    );
});

self.addEventListener('notificationclick', e => {
    e.waitUntil(
        //which ones are the pwa ones, which ones live in the browser?
        self.clients.matchAll({type: 'window'}).map(clients => {
            if (clients.length > 0) {
                //TODO: have preference?
                clients[0].focus();
                //TODO: go to payload parent
            } else {
                self.clients.openWindow('/');
                //TODO: go to payload parent
            }
        })
    );
});

//TODO: integration test!
self.addEventListener('pushsubscriptionchange', e => {
    log("ServiceWorker received pushsubscriptionchange event", e);
    // resubscribe and send new subscription to backend
    e.waitUntil(
        getPublicKey().flatMap(publicKey => publicKey.json().flatMap ( publicKeyJson => {
            if (publicKey) {
                return self.registration.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: Uint8Array.from(atob(publicKeyJson), c => c.charCodeAt(0))
                }).flatMap(sendSubscriptionToBackend);
            } else {
                return Promise.empty;
            }
        }
        ))
    );
});

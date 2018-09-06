workbox.skipWaiting();
workbox.clientsClaim();

// https://developers.google.com/web/tools/workbox/guides/precache-files/webpack
workbox.precaching.precacheAndRoute(self.__precacheManifest || []);

workbox.routing.registerRoute(
  new RegExp('/(index.html)?'),
    workbox.strategies.networkFirst({
        networkTimeoutSeconds: 2
    })
);

// cache google fonts
workbox.routing.registerRoute(
  new RegExp('https://fonts.(?:googleapis|gstatic).com/(.*)'),
  workbox.strategies.cacheFirst({
    cacheName: 'google-fonts',
    plugins: [
      new workbox.expiration.Plugin({
        maxEntries: 30,
      }),
      new workbox.cacheableResponse.Plugin({
        statuses: [0, 200]
      }),
    ],
  }),
);

/////////////////////////////////////////

function wrapConsoleCall(funName) {
    return function() {
        arguments[0] = "[SW] " + arguments[0];
        return console[funName].apply(console, arguments);
    };
}
const log = wrapConsoleCall("log");
const warn = wrapConsoleCall("warn");
const error = wrapConsoleCall("error");

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
    return db().then(db => {
        let transaction = db.transaction(["auth"], "readwrite");
        let store = transaction.objectStore("auth");
        return requestPromise(store.get(0));
    });
}

function getPublicKey() {
    return fetch(baseUrl + '/Push/getPublicKey', { method: 'POST', body: '{}' }); // TODO: use empty payload?
}
const logToBackend = s => fetch(baseUrl + '/Api/log', {
    method: 'POST',
    body: JSON.stringify({ message: s })
});

function sendSubscriptionToBackend(subscription, currentAuth) {
    log("sendSubscriptionToBackend: ", subscription);

    if (!subscription || !subscription.getKey) { // current subscription can be null if user did not enable it
        return Promise.reject("Cannot send subscription to backend, subscription is empty.");
    }

    let key = subscription.getKey('p256dh');
    let auth = subscription.getKey('auth');
    if (!key || !auth) {
        return Promise.reject("Cannot send subscription to backend, key or auth is missing, ignoring: key: " + key + ", auth: " + auth);
    }

    let subscriptionObj = {
        endpointUrl: subscription.endpoint,
        p256dh: btoa(String.fromCharCode.apply(null, new Uint8Array(key))),
        auth: btoa(String.fromCharCode.apply(null, new Uint8Array(auth)))
    };

    log("Sending subscription to backend", subscriptionObj);
    return fetch(baseUrl + '/Push/subscribeWebPush', {
        method: 'POST',
        body: JSON.stringify({ subscription: subscriptionObj }),
        headers: {
            'Authorization': currentAuth
        }
    });
}

// TODO: check if permissions granted, otherwise we don't need this. in this
// case the app will do this when request notification permissions.
function subscribeWebPushAndPersist() {
    log("Subscribing to web push");
    currentAuth().then(currentAuth => {
        if (currentAuth) {
            getPublicKey().then(publicKey => publicKey.json().then ( publicKeyJson => {
                if (publicKey) {
                    log("publicKey: ", publicKey);
                    return self.registration.pushManager.subscribe({
                        userVisibleOnly: true,
                        applicationServerKey: Uint8Array.from(atob(publicKeyJson), c => c.charCodeAt(0))
                    }).then(sub => sendSubscriptionToBackend(sub, currentAuth));
                } else {
                    return Promise.reject("Cannot subscribe, no public key.");
                }
            }));
        } else {
            return Promise.reject("Cannot subscribe, no authentication.");
        }
    });
}

// startup
log("ServiceWorker starting!");
port = location.port ? ":" + location.port : '';
const baseUrl = location.protocol + '//core.' + location.hostname + port + '/api';
log("BaseUrl: " + baseUrl);

// subscribe to webpush on startup
self.addEventListener('activate', e => {
    e.waitUntil(
        subscribeWebPushAndPersist()
    );
});

// https://serviceworke.rs/push-subscription-management_service-worker_doc.html
self.addEventListener('push', e => {
    log("ServiceWorker received push notification", e);
    if(Notification.permission != "granted") {
        log("ServiceWorker received but notifications are not granted, ignoring");
        return;
    }

    e.waitUntil(
        self.clients.matchAll({type: 'window'}).then(clients => {
            if (clients.length > 0) {
                return Promise.reject("ServiceWorker has active clients, ignoring push notification.");
            } else {
                let body = e.data ? e.data.text() : 'Push message no payload';
                let options = {
                    body: body,
                    icon: 'favicon.ico',
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
        self.clients.matchAll({type: 'window'}).then(clients => {
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
// https://serviceworke.rs/push-subscription-management_service-worker_doc.html
self.addEventListener('pushsubscriptionchange', e => {
    log("ServiceWorker received pushsubscriptionchange event", e);
    // resubscribe and send new subscription to backend
    e.waitUntil(subscribeWebPushAndPersist());
});

// to test push renewal, trigger event manually:
// setTimeout(() => self.dispatchEvent(new ExtendableEvent("pushsubscriptionchange")), 3000);


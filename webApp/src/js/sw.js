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
                if (publicKeyJson) {
                    log("publicKey: ", publicKeyJson);
                    return self.registration.pushManager.subscribe({
                        userVisibleOnly: true,
                        applicationServerKey: Uint8Array.from(atob(publicKeyJson), c => c.charCodeAt(0))
                    }).then(sub => {
                        log("Success. Sending subscription to backend");
                        sendSubscriptionToBackend(sub, currentAuth)
                    });
                } else {
                    log("Cannot subscribe, no public key.");
                    return Promise.reject("Cannot subscribe, no public key.");
                }
            }));
        } else {
            log("Cannot subscribe, no authentication.");
            return Promise.reject("Cannot subscribe, no authentication.");
        }
    });
}

function focusedClient(windowClients, subscribedId, channelId, messageId) {
    let clientIsFocused = false;
    for (let i = 0; i < windowClients.length; i++) {
        const windowClient = windowClients[i];
        if (windowClient.focused) {
            const url = windowClient.url;
            if(url.indexOf(subscribedId) !== -1 || url.indexOf(channelId) !== -1 || url.indexOf(messageId) !== -1) {
                clientIsFocused = true;
                break;
            }
        }
    }
    return clientIsFocused;
}

// startup
log("ServiceWorker starting!");
const port = location.port ? ":" + location.port : '';
const baseUrl = location.protocol + '//core.' + location.hostname + port + '/api';
log("BaseUrl: " + baseUrl);
log("Origin: " + location.origin);

// Weird workaround since emoji requires global
let global = {};
importScripts('emoji.min.js');
let pushEmojis = new global.EmojiConvertor();
pushEmojis.init_env();
pushEmojis.include_title = false;
pushEmojis.allow_native = true;
pushEmojis.wrap_native = false;
pushEmojis.avoid_ms_emoji = true;
pushEmojis.replace_mode = "unified";

// subscribe to webpush on startup
self.addEventListener('activate', e => {
    log("Trying to subcribe to webpush");
    e.waitUntil(
        subscribeWebPushAndPersist()
    );
});

// https://serviceworke.rs/push-subscription-management_service-worker_doc.html
self.addEventListener('push', e => {

    log("ServiceWorker received push notification", e);
    console.log(e);

    if(Notification.permission != "granted") {
        log("ServiceWorker received but notifications are not granted, ignoring");
        return;
    }

    e.waitUntil(
        self.clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        }).then(windowClients => {

            if (e.data) {
                const data = e.data.json();
                const nodeId = data.nodeId;
                const parentId = data.parentId;
                const subscribedId = data.subscribedId;
                const channelId = (!!parentId) ? parentId : subscribedId;

                if (focusedClient(windowClients, subscribedId, channelId, nodeId)) {
                    log("focused client => ignoring push");
                    return;
                } else {
                    log("no focused client found");

                    const titleContent = (!!data.parentContent && !!parentId && parentId != subscribedId) ? `${data.subscribedContent} / ${data.parentContent}` : data.subscribedContent;
                    const user = (data.username.indexOf('unregistered-user') !== -1) ? 'Unregistered User' : data.username;
                    const content = data.content ? `${user}: ${pushEmojis.replace_emoticons(data.content)}` : user;

                    let options = {
                        body: content,
                        icon: 'favicon.ico',
                        vibrate: [100, 50, 100],
                        renotify: true,
                        tag: subscribedId,
                        // actions: [
                        //     { action: 'explore', title: 'Explore this new world' },
                        //     { action: 'close', title: 'Close', icon: 'images/xmark.png'},
                        // ],
                        data: {
                            dateOfArrival: Date.now(),
                            nodeId: nodeId,
                            channelId: channelId,
                            msgCount: 1
                        },
                    };

                    return registration.getNotifications().then(notifications => {
                        let count = 1;

                        for(let i = 0; i < notifications.length; i++) {
                            if (notifications[i].data &&
                                notifications[i].data.subscribedId === subscribedId) {
                                count = notifications[i].data.msgCount + 1;
                                options.data.msgCount = count;
                                notifications[i].close();
                            }
                        }

                        log(`number of notifications = ${count}`);

                        const title = (count > 1) ? `${titleContent} (${count} new messages)` : titleContent;

                        return self.registration.showNotification(pushEmojis.replace_emoticons(title), options);
                    });
                }
            } else {
                    log("push notification without data => ignoring");
                    return;
            }
        })
    );
});

self.addEventListener('notificationclick', e => {

    log("ServiceWorker received notification click");
    console.log(e);

    e.notification.close();
    e.waitUntil(

        //which ones are the pwa ones, which ones live in the browser?
        self.clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        }).then(windowClients => {

            const ndata = e.notification.data;
            const messageId = ndata.nodeId;
            const channelId = ndata.channelId;
            const baseLocation = "https://staging.woost.space";

            for (const index in windowClients) {

                const client = windowClients[index];
                const url = client.url;

                if (url.indexOf(channelId) !== -1 || url.indexOf(messageId) !== -1) {
                    log("Found window that is already including node");

                    return client.focus().then(function (client) { client.navigate(url); });
                } else if (url.indexOf(baseLocation) !== -1) {
                    log("Found woost window => opening node");

                    const exp = /(?!(page=))((([a-zA-z0-9]{22})[,:]?)+)/
                    const newLocation = (url.search(exp) !== -1) ? url.replace(exp, channelId) : (baseLocation + "/#view=conversation&page=" + channelId);
                    return client.focus().then(function (client) { client.navigate(newLocation); });
                }
            }

            log("no matching client found. Opening new window");

            return self.clients.openWindow("/#view=conversation&page=" + channelId).then(function (client) { client.focus(); });

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


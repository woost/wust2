importScripts('/workbox-v4.3.1/workbox-sw.js');
workbox.setConfig({modulePathPrefix: "/workbox-v4.3.1"});

// globals
const port = location.port ? ":" + location.port : '';
const baseUrl = location.protocol + '//core.' + location.hostname + port + '/api';
const isDebug = location.hostname == "localhost"
var userAuth;

// logging

const logToBackend = s => fetch(baseUrl + '/Api/log', {
    method: 'POST',
    body: JSON.stringify({ message: s })
});

const wrapConsoleCall = (funName, enabled) => enabled ? (...args) => console[funName].apply(console, ["[SW]"].concat(args)) : () => {}
const log = wrapConsoleCall("log", isDebug);
const warn = wrapConsoleCall("warn", true);
const error = wrapConsoleCall("error", true);

log("ServiceWorker starting!");

// workbox settings
workbox.routing.registerRoute(
  /\.(?:html)$/,
  new workbox.strategies.NetworkFirst({
    cacheName: 'html-resources',
  })
);

workbox.routing.registerRoute(
  /\.(?:js|css)$/,
  new workbox.strategies.CacheFirst({
    cacheName: 'static-resources',
  })
);

workbox.routing.registerRoute(
  new RegExp('\\.(?:png|gif|jpg|jpeg|svg)$'),
  new workbox.strategies.CacheFirst({
    cacheName: 'images',
    plugins: [
      new workbox.expiration.Plugin({
        maxEntries: 50,
        purgeOnQuotaError: true,
      }),
    ],
  }),
);

workbox.routing.registerRoute(
  /^https:\/\/fonts\.googleapis\.com/,
  new workbox.strategies.StaleWhileRevalidate({
    cacheName: 'google-fonts-stylesheets',
  })
);

workbox.routing.registerRoute(
  /^https:\/\/fonts\.gstatic\.com/,
  new workbox.strategies.CacheFirst({
    cacheName: 'google-fonts-webfonts',
    plugins: [
      new workbox.cacheableResponse.Plugin({
        statuses: [0, 200],
      }),
      new workbox.expiration.Plugin({
        maxAgeSeconds: 60 * 60 * 24 * 365,
        maxEntries: 30,
        purgeOnQuotaError: true,
      }),
    ],
  })
);

/////////////////////////////////////////

function getPublicKey() {
    return fetch(baseUrl + '/Push/getPublicKey', { method: 'POST', body: '{}' }); // TODO: use empty payload?
}
function sendSubscriptionToBackend(subscription) {

    if (!subscription || !subscription.getKey) { // current subscription can be null if user did not enable it
        return Promise.reject("Cannot send subscription to backend, subscription is empty.");
    }

    let key = subscription.getKey('p256dh');
    let auth = subscription.getKey('auth');
    if (!key || !auth) {
        return Promise.reject("Cannot send subscription to backend, key or auth is missing, ignoring.");
    }

    let subscriptionObj = {
        endpointUrl: subscription.endpoint,
        p256dh: btoa(String.fromCharCode.apply(null, new Uint8Array(key))),
        auth: btoa(String.fromCharCode.apply(null, new Uint8Array(auth)))
    };

    log("Sending subscription to backend.");
    return fetch(baseUrl + '/Push/subscribeWebPush', {
        method: 'POST',
        body: JSON.stringify({ subscription: subscriptionObj }),
        headers: {
            'Authorization': userAuth
        }
    });
}

// TODO: check if permissions granted, otherwise we don't need this. in this
// case the app will do this when request notification permissions.
function updateWebPushSubscriptionAndPersist() {
    log("Trying to subscribe to web push.");
    if (userAuth) {
        getPublicKey().then(
            publicKey => publicKey.json().then (
                publicKeyJson => {
                    if (publicKeyJson) {
                        log("Found public key.");
                        // we unsubscribe first, because you cannot subscribe with a new public key if there is an old subscription active.
                        // for now, we always unsubscribe first before subscribing to webpush.
                        return self.registration.pushManager.getSubscription().then(subscription => {
                            //TODO: Test if this is still necessary
                            let unsubscribePromise = subscription ? subscription.unsubscribe() : Promise.resolve(true);
                            return unsubscribePromise.then(successful => {
                                return self.registration.pushManager.subscribe({
                                    userVisibleOnly: true,
                                    applicationServerKey: Uint8Array.from(atob(publicKeyJson.replace(/-/g,'+').replace(/_/g,'/')), c => c.charCodeAt(0))
                                }).then(
                                    sub => {
                                        log("Success. Sending subscription to backend.");
                                        sendSubscriptionToBackend(sub);
                                    },
                                    err => {
                                        error(`Subscribing failed with: ${err}.`);
                                        logToBackend(`Subscribing failed with: ${err}.`);
                                    }
                                );
                            });
                        });
                    }
                },
                err => {
                    logToBackend(`Decoding of json public key ${publicKey} failed with: ${err}.`);
                    error(`Decoding of json public key failed with: ${err}.`);
                }
            )
        );
    } else {
        log("Cannot subscribe, no authentication. Will unsubscribe.");
        return self.registration.pushManager.getSubscription().then(subscription => {
            subscription ? subscription.unsubscribe() : Promise.resolve(true);
        });
    }
}

function focusedClient(windowClients, subscribedId, messageId) {
    let clientIsFocused = false;
    for (let i = 0; i < windowClients.length; i++) {
        const windowClient = windowClients[i];
        if (windowClient.focused) {
            const url = windowClient.url;
            if(url.indexOf(subscribedId) !== -1 || url.indexOf(messageId) !== -1) {
                clientIsFocused = true;
                break;
            }
        }
    }
    return clientIsFocused;
}

// when a new serviceworker is available:
// https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/skipWaiting
// https://stackoverflow.com/questions/38168276/navigator-serviceworker-controller-is-null-until-page-refresh
self.addEventListener('install', function(event) {
    log("ServiceWorker installed");
    // Activate worker immediately
    self.skipWaiting();
});
//self.addEventListener('activate', function(event) {
//    log("ServiceWorker activated");
//    //subscribe to webpush on startup
//    event.waitUntil(updateWebPushSubscriptionAndPersist());
//});

self.addEventListener('message', e => {
    if(e.data) {
        const messageObject = JSON.parse(e.data);
        if(messageObject.type === "AuthMessage") {
            log("Received auth message.");
            if(messageObject.token && (userAuth !== messageObject.token)) {
                userAuth = messageObject.token;
                e.waitUntil(updateWebPushSubscriptionAndPersist());
            } else {
                log("Current auth is still valid");
            }
        } else if(messageObject.type === "DeAuthMessage") {
            log("Received deauth message.");
            userAuth = undefined
            e.waitUntil(updateWebPushSubscriptionAndPersist());
        } else if(messageObject.type === "Message") {
            log("Received worker message.");
        } else {
            log("Received unclassified message.");
        }
    }
});

// https://serviceworke.rs/push-subscription-management_service-worker_doc.html
self.addEventListener('push', e => {

    log("ServiceWorker received push notification.");

    if(Notification.permission != "granted") {
        log("Push received but notifications are not granted, ignoring.");
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
                const description = data.description ? ` (${data.description})` : ""
                const outdated = (!!data.epoch) ? ((Date.now() - Number(data.epoch)) > 43200000) : false;

                // 86400000 == 1 day (1000*60*60*24)
                // 43200000 == 12h   (1000*60*60*12)
                if (outdated || !data.content || focusedClient(windowClients, subscribedId, subscribedId, nodeId)) {
                    log("Focused client or outdated push notification => ignoring push.");
                    return;
                } else if (data.version != 1) {
                    // guard against incompatible push message version
                    log(`Received message with different version (expected: 1, got ${data.version}`)
                    return;
                } else {
                    log("No focused client found.");

                    const titleContent = (!data.parentContent | !parentId | parentId == subscribedId) ? `${data.subscribedContent}${description}` : `${data.subscribedContent} / ${data.parentContent}${description}`;
                    const user = (!data.username || data.username.indexOf('unregistered-user') !== -1) ? 'Unregistered User' : data.username;
                    const content = `${user}: ${data.content}`;

                    let options = {
                        body: content,
                        icon: 'static/logo.ico',
                        vibrate: [150, 50, 150],
                        renotify: true,
                        tag: subscribedId,
                        // actions: [
                        //     { action: 'explore', title: 'Explore this new world' },
                        //     { action: 'close', title: 'Close', icon: 'images/xmark.png'},
                        // ],
                        data: {
                            nodeId: nodeId,
                            subscribedId: subscribedId,
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

                        log(`Number of notifications = ${count}.`);

                        const title = (count > 1) ? `${titleContent} (${count}).` : titleContent;

                        return self.registration.showNotification(title, options);
                    });
                }
            } else {
                    log("Push notification without data => ignoring.");
                    return;
            }
        })
    );
});

self.addEventListener('notificationclick', e => {

    log("ServiceWorker received notification click.");

    e.notification.close();
    e.waitUntil(

        //which ones are the pwa ones, which ones live in the browser?
        self.clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        }).then(windowClients => {

            const ndata = e.notification.data;
            const messageId = ndata.nodeId;
            const subscribedId = ndata.subscribedId;
            const baseLocation = "woost.space";

            for (const index in windowClients) {

                const client = windowClients[index];
                const url = client.url;

                if (url.indexOf(subscribedId) !== -1 || url.indexOf(messageId) !== -1) {
                    log("Found window that is already including node.");

                    return client.focus().then(function (client) { client.navigate(url); });
                } else if (url.indexOf(baseLocation) !== -1) {
                    log("Found woost window => opening node.");

                    const exp = /(?!(page=))((([a-zA-z0-9]{22})[,:]?)+)/
                    const newLocation = (url.search(exp) !== -1) ? url.replace(exp, subscribedId) : ("/#page=" + subscribedId);
                    return client.focus().then(function (client) { client.navigate(newLocation); });
                }
            }

            log("No matching client found. Opening new window.");

            return self.clients.openWindow("/#page=" + subscribedId).then(function (client) { client.focus(); });

        })
    );
});

//TODO: integration test!
// https://serviceworke.rs/push-subscription-management_service-worker_doc.html
self.addEventListener('pushsubscriptionchange', e => {
    log("ServiceWorker received pushsubscriptionchange event.");
    // resubscribe and send new subscription to backend
    e.waitUntil(updateWebPushSubscriptionAndPersist());
});

// to test push renewal, trigger event manually:
// setTimeout(() => self.dispatchEvent(new ExtendableEvent("pushsubscriptionchange")), 3000);


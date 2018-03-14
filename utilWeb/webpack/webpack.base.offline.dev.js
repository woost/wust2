const OfflinePlugin = require("offline-plugin");

module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: false,
                events: true,
                entry: "../../../../../utilWeb/webpack/sw-entry.js"
            },
            AppCache: false
        })
    ]
}

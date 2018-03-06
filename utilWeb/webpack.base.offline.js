const OfflinePlugin = require("offline-plugin");

module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: false,
                events: true,
                entry: "./sw-entry.js"
            },
            AppCache: false
        })
    ]
}

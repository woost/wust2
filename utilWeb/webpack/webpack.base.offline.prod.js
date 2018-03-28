const OfflinePlugin = require("offline-plugin");

// https://github.com/NekR/offline-plugin/blob/master/docs/options.md
module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: true,
                events: true,
                entry: "./sw-entry.js"
            },
            AppCache: false
        })
    ]
}

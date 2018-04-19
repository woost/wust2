const OfflinePlugin = require("offline-plugin");

// https://github.com/NekR/offline-plugin/blob/master/docs/options.md
module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: false,
                events: true,
                entry: "../../../../../webApp/src/js/sw-entry.js"
            },
            AppCache: false
        })
    ]
}

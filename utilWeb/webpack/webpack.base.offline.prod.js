const OfflinePlugin = require("offline-plugin");

// https://github.com/NekR/offline-plugin/blob/master/docs/options.md
module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: false, // TODO does not work anymore since webpack 4
                events: true,
                entry: "./sw-entry.js"
            },
            AppCache: false
        })
    ]
}

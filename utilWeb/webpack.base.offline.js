const OfflinePlugin = require("offline-plugin");

module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: true,
                events: true
            },
            AppCache: false
        })
    ]
}

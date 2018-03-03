const OfflinePlugin = require("offline-plugin");

module.exports = {
    plugins: [
        new OfflinePlugin({
            ServiceWorker: {
                minify: true
            },
            AppCache: false
        })
    ]
}

const {InjectManifest} = require('workbox-webpack-plugin');

// https://developers.google.com/web/tools/workbox/modules/workbox-webpack-plugin#injectmanifest_plugin_1
module.exports = {
    plugins: [
        new InjectManifest({
            swSrc: '../../../../src/js/sw.js',
            swDest: 'sw.js',
            importWorkboxFrom: 'local', // will copy all of the Workbox runtime libraries into a versioned directory alongside your generated service worker, and configure the service worker to use those local copies. 
            include: [/\.html$/, /\.js$/, /\.css$/, /\.svg$/]
        })
    ]
};

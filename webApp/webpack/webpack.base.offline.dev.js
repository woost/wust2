const {InjectManifest} = require('workbox-webpack-plugin');

module.exports = {
    plugins: [
        new InjectManifest({
            swSrc: '../../../../../webApp/src/js/sw-entry.js',
            swDest: 'sw.js',
            importWorkboxFrom: 'local', // will copy all of the Workbox runtime libraries into a versioned directory alongside your generated service worker, and configure the service worker to use those local copies. 
        })
    ]
};

const Webpack = require('webpack');
const Path = require('path');

const rootDir = Path.resolve(__dirname, '../../../../..');

module.exports = require('./scalajs.webpack.config');

const projectRoot = Path.resolve(rootDir, 'assets/project-root'); // assets/sources is a symlink to the root folder

// https://webpack.js.org/configuration/dev-server
module.exports.devServer = {

    port: process.env.WUST_DEVSERVER_PORT,
    contentBase: [
        // __dirname,
        Path.resolve(__dirname, 'fastopt'), // fastOptJS output
        Path.resolve(rootDir, 'assets/public'), // css files, ...
        Path.resolve(rootDir, 'assets/dev'), // development index.html
        projectRoot, // serve project files for source-map access
    ],
    watchContentBase: true,
    watchOptions: {
        ignored: projectRoot
    },
    // watchOptions: { poll: true },
    open: false, // open page in browser
    hotOnly: true, // only reload when build is sucessful
    inline: true, // show build errors in browser console
    overlay: true, // show full screen overlay for compile errors
    allowedHosts: [
        ".localhost"
    ],

    //proxy websocket requests to app
    proxy : [
        subdomainProxy("core", process.env.WUST_BACKEND_PORT, true),
        subdomainProxy("github", 54321)
    ],
    compress: (process.env.WUST_DEVSERVER_COMPRESS == 'true')
};

module.exports.plugins = [
    new Webpack.HotModuleReplacementPlugin()
];

function subdomainProxy(subdomain, port, ws) {
    ws = !!ws;
    var protocol = ws ? "ws" : "http";
    var url = protocol + "://localhost:" + port;

    return {
        path: '/*',
        ws: ws,
        target: url,
        bypass: function (req, res, proxyOptions) {
            if (req.headers.host.startsWith(subdomain + ".")) return true
            else return req.path;
        }
    };
}

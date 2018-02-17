const Webpack = require('webpack');
const Path = require('path');

const rootDir = Path.resolve(__dirname, '../../../../..');

module.exports = require('./scalajs.webpack.config');

const projectRoot = Path.resolve(rootDir, 'assets/project-root'); // assets/sources is a symlink to the root folder

// https://webpack.js.org/configuration/dev-server
module.exports.devServer = {

    port: process.env.WUST_PORT,
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
    host: "0.0.0.0", //TODO this is needed so that in dev docker containers can access devserver through docker bridge
    allowedHosts: [ ".localhost" ],

    //proxy websocket requests to app
    proxy : [
        subdomainProxy("core", process.env.WUST_CORE_PORT, true),
        subdomainProxy("github", process.env.WUST_GITHUB_PORT),

        {
            path: '/web-app', // web app with production assets
            target: 'http://localhost:' + process.env.WUST_WEB_PORT,
            pathRewrite: {"^/web-app" : ""}
        }
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

const Webpack = require('webpack');
const HtmlPlugin = require("html-webpack-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const Path = require('path');

const commons = require('./webpack.base.common.js');
const dirs = commons.woost.dirs;
const appName = commons.woost.appName;
module.exports = commons.webpack;

const filenamePattern = '[name]';

////////////////////////////////////////
// html template generate index.html
////////////////////////////////////////
//TODO does not trigger when only changing html template file
module.exports.plugins.push(new HtmlPlugin({
    title: 'Woost-DEV',
    template: Path.join(dirs.assets, 'index.template.html'),
}));

////////////////////////////////////////
// styles generated from scss
////////////////////////////////////////
const extractSass = new ExtractTextPlugin({
    filename: filenamePattern + '.css'
});
module.exports.plugins.push(extractSass);
module.exports.module.rules.push({
    test: /style\.scss$/,
    use: extractSass.extract({
        use: [{ loader: "css-loader", options: { sourceMap: true }}, { loader: "sass-loader", options: { sourceMap: true }}],
    })
});
module.exports.module.rules.push({
    test: /\.css$/,
    use: extractSass.extract({
        use: [{ loader: "css-loader", options: { sourceMap: true }}],
    })
});

////////////////////////////////////////
// dev server
////////////////////////////////////////
module.exports.devServer = {
    // https://webpack.js.org/configuration/dev-server
    port: process.env.WUST_PORT,
    contentBase: [
        module.exports.output.path,
        dirs.projectRoot, // serve project files for source-map access
    ],
    watchContentBase: true,
    watchOptions: {
        ignored: dirs.projectRoot
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
        },
        {
            path: '/pwa-app', // web app with production assets
            target: 'http://localhost:' + process.env.WUST_PWA_PORT,
            pathRewrite: {"^/pwa-app" : ""}
        }
    ],
    compress: (process.env.WUST_DEVSERVER_COMPRESS == 'true')
};

module.exports.plugins.push(new Webpack.HotModuleReplacementPlugin())

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

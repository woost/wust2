const Webpack = require('webpack');
const HtmlPlugin = require("html-webpack-plugin");
const HtmlAssetsPlugin = require("html-webpack-include-assets-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const Path = require('path');

const commons = require('./webpack.base.common.js');
const dirs = commons.woost.dirs;
const appName = commons.woost.appName;
const cssFiles = commons.woost.cssFiles;
module.exports = commons.webpack;

module.exports.mode = 'development';

module.exports.output.path = Path.join(__dirname, "dev");

////////////////////////////////////////
// add additional generated js files from libraryOnly bundlingmode
////////////////////////////////////////
const baseJsFile = appName + '.js';
const loaderJsFile = appName + '-loader.js';
//this would bundle all js files into one
// module.exports.entry[appName].push('./' + baseJsFile);
// module.exports.entry[appName].push('./' + loaderJsFile);
const extraAssets = [ loaderJsFile, baseJsFile ].concat(cssFiles.map(function(f) { return Path.basename(f); }));

////////////////////////////////////////
// html template generate index.html
////////////////////////////////////////
module.exports.plugins.push(new HtmlPlugin({
    title: 'dev',
    template: Path.join(dirs.assets, 'index.template.html'),
    favicon: Path.join(dirs.assets, 'favicon.ico'),
    showErrors: true
}));
module.exports.plugins.push(new HtmlAssetsPlugin({ assets: extraAssets, append: true }))

////////////////////////////////////////
// dev server
////////////////////////////////////////
module.exports.devServer = {
    // https://webpack.js.org/configuration/dev-server
    port: process.env.WUST_PORT,
    contentBase: [
        module.exports.output.path,
        dirs.assets
        //dirs.projectRoot, // serve project files for source-map access
    ],
    watchContentBase: true,
    open: false, // open page in browser
    hot: false,
    hotOnly: false, // only reload when build is sucessful
    inline: true, // show build errors in browser console
    overlay: false, // this breaks the compiled app-fastopt-library.js
    host: "0.0.0.0", //TODO this is needed so that in dev docker containers can access devserver through docker bridge
    allowedHosts: [ ".localhost" ],

    //proxy websocket requests to app
    proxy : [
        //TODO: subdomain and path proxy?
        setupProxy({ /*subdomain: "core", */path: "ws", port: process.env.WUST_CORE_PORT, ws: true }),
        setupProxy({ /*subdomain: "core", */path: "api", port: process.env.WUST_CORE_PORT }),
        setupProxy({ subdomain: "core", port: process.env.WUST_CORE_PORT }),
        setupProxy({ subdomain: "github", port: process.env.WUST_GITHUB_PORT, pathRewrite: true }),
        setupProxy({ path: "apps/web", port: process.env.WUST_WEB_PORT, pathRewrite: true })
    ],
    compress: (process.env.WUST_DEVSERVER_COMPRESS == 'true')
};
// module.exports.plugins.push(new Webpack.HotModuleReplacementPlugin())

function setupProxy(config) {
    var ws = !!config.ws;
    var protocol = config.ws ? "ws" : "http";
    var url = protocol + "://localhost:" + config.port;
    return {
        ws: ws,
        target: url,
        path: config.path ? ('/' + config.path) : '/*',
        pathRewrite: (config.path && config.pathRewrite) ? ({
            ["^/" + config.path]: ""
        }) : undefined,
        bypass: config.subdomain ? (function (req, res, proxyOptions) {
            if (req.headers.host.startsWith(config.subdomain + ".")) return true
            else return req.path;
        }) : undefined
    };
}

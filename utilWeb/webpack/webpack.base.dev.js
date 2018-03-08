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
    title: 'Woost-DEV',
    template: Path.join(dirs.assets, 'index.template.html'),
    favicon: Path.join(dirs.sharedAssets, 'icon.ico'),
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
        dirs.assets,
        dirs.sharedAssets
        //dirs.projectRoot, // serve project files for source-map access
    ],
    watchContentBase: true,
    watchOptions: {
        ignored: [
            // dirs.projectRoot,
            // function(file) {
            //     var shouldReload = (file == module.exports.output.path) || file.startsWith(dirs.assets) || file.startsWith(dirs.sharedAssets) || (file == Path.join(module.exports.output.path, '_fastopt_file_'));
            //     if (shouldReload)
            //         console.log(file, shouldReload);
            //     return !shouldReload;
            // }
        ]
    },
    // watchOptions: { poll: true },
    open: false, // open page in browser
    hot: false,
    hotOnly: false, // only reload when build is sucessful
    inline: true, // show build errors in browser console
    overlay: false, // this breaks the compiled app-fastopt-library.js
    host: "0.0.0.0", //TODO this is needed so that in dev docker containers can access devserver through docker bridge
    allowedHosts: [ ".localhost" ],

    //proxy websocket requests to app
    proxy : [
        subdomainProxy("core", process.env.WUST_CORE_PORT, true),
        subdomainProxy("github", process.env.WUST_GITHUB_PORT),
        pathProxy("apps/web", process.env.WUST_WEB_PORT),
        pathProxy("apps/pwa", process.env.WUST_PWA_PORT)
    ],
    compress: (process.env.WUST_DEVSERVER_COMPRESS == 'true')
};
// module.exports.plugins.push(new Webpack.HotModuleReplacementPlugin())

function basicProxy(port, ws) {
    ws = !!ws;
    var protocol = ws ? "ws" : "http";
    var url = protocol + "://localhost:" + port;
    return {
        ws: ws,
        target: url
    }
}
function subdomainProxy(subdomain, port, ws) {
    return Object.assign(basicProxy(port, ws), {
        path: '/*',
        bypass: function (req, res, proxyOptions) {
            if (req.headers.host.startsWith(subdomain + ".")) return true
            else return req.path;
        }
    });
}
function pathProxy(path, port, ws) {
    return Object.assign(basicProxy(port, ws), {
        path: '/' + path,
        pathRewrite: {
            ["^/" + path]: ""
        }
    });
}

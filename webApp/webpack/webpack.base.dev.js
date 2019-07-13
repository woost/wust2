const Webpack = require('webpack');

const CopyPlugin = require("copy-webpack-plugin");
const HtmlPlugin = require("html-webpack-plugin");
const HtmlAssetsPlugin = require("html-webpack-include-assets-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const WasmPackPlugin = require("@wasm-tool/wasm-pack-plugin");

const childProcess = require('child_process');

const Path = require('path');

const commons = require('./webpack.base.common.js');
const dirs = commons.woost.dirs;
const appName = commons.woost.appName;
const cssFolder = commons.woost.cssFolder;
const cssFiles = commons.woost.cssFiles.filter(x => !x.endsWith('scalacss.css')); //filter out generated css file...
const htmlTemplateFile = commons.woost.htmlTemplateFile;
const staticIncludeAssets = commons.woost.staticIncludeAssets;
const staticCopyAssets = commons.woost.staticCopyAssets;
const versionString = commons.woost.versionString;
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
module.exports.plugins.push(new CopyPlugin(staticCopyAssets));
const extraAssets = staticIncludeAssets.concat([ loaderJsFile, baseJsFile ]).concat(cssFiles.map(function(f) { return Path.basename(f); }));

////////////////////////////////////////
// html template generate index.html
////////////////////////////////////////
module.exports.plugins.push(new HtmlPlugin({
    versionString: versionString,
    title: 'dev',
    template: htmlTemplateFile,
    favicon: Path.join(dirs.assets, 'favicon.ico'),
    showErrors: true
}));
module.exports.plugins.push(new HtmlAssetsPlugin({ assets: extraAssets, append: true }));

// console.warn(Path.resolve(dirs.project, "crate"))
// childProcess.spawn("wasm-pack", ["build"], {stdio: [process.stdin, process.stdout, process.stderr], cwd: Path.resolve(dirs.project, "crate")});
// console.warn("-------");
module.exports.plugins.push(new WasmPackPlugin({
      crateDirectory: Path.resolve(dirs.project, "crate"),
      // Check https://rustwasm.github.io/wasm-pack/book/commands/build.html for
      // the available set of arguments.
      //
      // Default arguments are `--typescript --target browser --mode normal`.
      extraArgs: "--no-typescript",
 
      // Optional array of absolute paths to directories, changes to which
      // will trigger the build.
      // watchDirectories: [
      //   path.resolve(__dirname, "another-crate/src")
      // ],
 
      // The same as the `--out-dir` option for `wasm-pack`
      // outDir: "pkg",
 
      // The same as the `--out-name` option for `wasm-pack`
      // outName: "index",
 
      // If defined, `forceWatch` will force activate/deactivate watch mode for
      // `.rs` files.
      //
      // The default (not set) aligns watch mode for `.rs` files to Webpack's
      // watch mode.
      // forceWatch: true,
 
      // If defined, `forceMode` will force the compilation mode for `wasm-pack`
      //
      // Possible values are `development` and `production`.
      //
      // the mode `development` makes `wasm-pack` build in `debug` mode.
      // the mode `production` makes `wasm-pack` build in `release` mode.
      // forceMode: "development",
    }));

////////////////////////////////////////
// dev server
////////////////////////////////////////
module.exports.devServer = {
    // https://webpack.js.org/configuration/dev-server
    port: process.env.WUST_PORT,
    contentBase: [
        module.exports.output.path,
        dirs.assets,
        cssFolder,
        dirs.root // serve complete project for providing source-maps, needs to be ignored for watching
    ],
    watchContentBase: true,
    open: false, // open page in browser
    hot: false,
    hotOnly: false, // only reload when build is sucessful
    inline: true, // live reloading
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
        setupProxy({ subdomain: "slack", port: process.env.WUST_SLACK_PORT, pathRewrite: true }),
        setupProxy({ path: "apps/web", port: process.env.WUST_WEB_PORT, pathRewrite: true })
    ],
    compress: (process.env.DEV_SERVER_COMPRESS == 'true')
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

const Webpack = require('webpack');

const HtmlPlugin = require("html-webpack-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const Path = require('path');
const ConcatPlugin = require('webpack-concat-plugin');
const CopyPlugin = require("copy-webpack-plugin");
const HtmlAssetsPlugin = require("html-webpack-include-assets-plugin");
const { execSync } = require('child_process');

const commons = require('./webpack.base.common.js');
const woost = commons.woost;
const outputFileNamePattern = '[name]';

module.exports = commons.webpack;
module.exports.mode = 'development';
module.exports.output.path = Path.join(__dirname, "dev");
// we need -library postfix, because the name for webapp-fastopt is already taken by the scala-js generated dev main file.
// to avoid collision, we postfix the output pattern. sadly this applies for all chunks...
module.exports.output.filename = outputFileNamePattern + '-library.js';

////////////////////////////////////////
// link node_modules into output folder so that it can be served via webpack dev server
// the files are just included in the html.
////////////////////////////////////////
execSync(`mkdir -p ${module.exports.output.path}`);
execSync(`ln --force --symbolic ../node_modules ${module.exports.output.path}/node_modules`);

////////////////////////////////////////
// add all files as entries of assets chunk (including js files from scala-js in dev mode)
////////////////////////////////////////
const scalaJsLoaderFile = Path.join(__dirname, woost.appName + '-loader.js');
const scalaJsFile = Path.join(__dirname, woost.appName + '.js');
const staticCopyFiles = [ scalaJsLoaderFile ];
const staticIncludeFiles = woost.files.vendor.js;
const cssFilesWithoutScalaCss = woost.files.css.filter(x => !x.endsWith('scalacss.css')) // scalacss file is not needed in dev, will be injected in code.
module.exports.entry.assets = woost.files.vendor.assets.concat(cssFilesWithoutScalaCss).concat(woost.files.assets);

////////////////////////////////////////
// html template generate html files
////////////////////////////////////////

woost.files.html.forEach(htmlFile => {
    const isIndexHtml = Path.basename(htmlFile) == "index.html";
    if (isIndexHtml) {
        module.exports.plugins.push(new HtmlPlugin({
            templateParameters: woost.templateParametersFunction({
                title: "dev",
                audience: "dev"
            }),
            filename: "index.html",
            template: htmlFile,
            chunks: ["assets", woost.appName],
            chunksSortMode: 'manual',
            showErrors: true
        }));
    }
});
module.exports.plugins.push(new CopyPlugin(staticCopyFiles.map(f => { return { "from": f, "context": Path.dirname(f), "to": ''} })));
module.exports.plugins.push(new HtmlAssetsPlugin({ assets: staticIncludeFiles.map(f => Path.relative(__dirname, f)), append: false }))
module.exports.plugins.push(new HtmlAssetsPlugin({ assets: staticCopyFiles.concat([scalaJsFile]).map(f => Path.relative(__dirname, f)), append: true }))

////////////////////////////////////////
// merge sw files into one file
////////////////////////////////////////

module.exports.plugins.push(new ConcatPlugin({
    uglify: false,
    sourceMap: true,
    injectType: 'none',
    name: 'sw',
    fileName: '[name].js',
    filesToConcat: woost.files.sw
}));

////////////////////////////////////////
// bundle css files
////////////////////////////////////////
const extractCss = new ExtractTextPlugin({ filename: outputFileNamePattern + '.css' });
module.exports.plugins.push(extractCss);
module.exports.module.rules.push({
    test: /\.css$/,
    use: extractCss.extract({
        use: [{ loader: "css-loader" }],
    })
});

////////////////////////////////////////
// copy workbox files to dist for serviceworker to include, no hashing, just the files.
////////////////////////////////////////
module.exports.plugins.push(new CopyPlugin(woost.files.vendor.workbox.map(f => { return { "from": f, "to": `${Path.basename(woost.dirs.workbox)}/` } })));

////////////////////////////////////////
// Copy fonts/icons/images to output path
////////////////////////////////////////
const fileStaticLoader = {
    loader: 'file-loader',
    options: {
        name: 'static/[name].[ext]',
    },
};
const fileAssetsLoader = {
    loader: 'file-loader',
    options: {
        name: '[path][name].[ext]',
    },
};
const fileRootLoader = {
    loader: 'file-loader',
    options: {
        name: '[name].[ext]',
    },
};
module.exports.module.rules.push({
    test: /\/static\//,
    use: [ fileStaticLoader ]
});
module.exports.module.rules.push({
    test: /\.(png|jpe?g|ico|svg|gif|woff2?|ttf|eot)$/,
    exclude: /\/static\//,
    use: [ fileAssetsLoader ]
});
module.exports.module.rules.push({
      test: /(\.webmanifest|browserconfig\.xml)$/,
      use: [
        fileRootLoader,
        {
            loader: "app-manifest-loader",
            options: {
                publicPath: "/"
            }
        }
      ]
});

////////////////////////////////////////
// dev server
////////////////////////////////////////
if (!process.env.WUST_CORE_PORT) {
    throw "Environment Variable 'WUST_CORE_PORT' is missing. You seem to be running `sbt` without `start sbt`. Have better luck next time and good luck from the javascript community, they are rooting for you.";
}
module.exports.devServer = {
    // https://webpack.js.org/configuration/dev-server
    port: process.env.WUST_PORT,
    contentBase: [
        module.exports.output.path,
        woost.dirs.root // serve complete project for providing source-maps, needs to be ignored for watching
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
        woost.setupDevServerProxy({ /*subdomain: "core", */path: "ws", port: process.env.WUST_CORE_PORT, ws: true }),
        woost.setupDevServerProxy({ /*subdomain: "core", */path: "api", port: process.env.WUST_CORE_PORT }),
        woost.setupDevServerProxy({ subdomain: "core", port: process.env.WUST_CORE_PORT }),
        woost.setupDevServerProxy({ subdomain: "github", port: process.env.WUST_GITHUB_PORT, pathRewrite: true }),
        woost.setupDevServerProxy({ subdomain: "slack", port: process.env.WUST_SLACK_PORT, pathRewrite: true }),
    ],
    compress: (process.env.DEV_SERVER_COMPRESS == 'true')
};
// module.exports.plugins.push(new Webpack.HotModuleReplacementPlugin())

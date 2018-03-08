const Webpack = require('webpack');
const CopyPlugin = require("copy-webpack-plugin");
const CleanPlugin = require("clean-webpack-plugin");
const ZopfliPlugin = require("zopfli-webpack-plugin");
const BrotliPlugin = require('brotli-webpack-plugin');
const ClosureCompilerPlugin = require("webpack-closure-compiler");
const SriPlugin = require("webpack-subresource-integrity");
const HtmlPlugin = require("html-webpack-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const Path = require('path');
const fs = require("fs");

const commons = require('./webpack.base.common.js');
const dirs = commons.woost.dirs;
const appName = commons.woost.appName;
const cssFiles = commons.woost.cssFiles;
module.exports = commons.webpack;

// TODO bug with custom output in https://github.com/scalacenter/scalajs-bundler/issues/192
// expects the originally configured output file to exist, just create it.
const dummyOutputFile = Path.join(module.exports.output.path, module.exports.output.filename.replace('[name]', appName));
if (!fs.existsSync(dummyOutputFile)) {
    fs.closeSync(fs.openSync(dummyOutputFile, 'w'));
}

// set up output path
module.exports.output.path = Path.join(__dirname, "dist");
// module.exports.output.publicPath = "/";

// copy some assets to dist folder
module.exports.plugins.push(new CopyPlugin([
    dirs.sharedAssets + "*.ico",
    dirs.sharedAssets + "*.svg",
    dirs.sharedAssets + "*.png",
    dirs.sharedAssets + "manifest.json"
]));

const filenamePattern = '[name].[chunkhash]';
module.exports.output.filename = filenamePattern + '.js';
// module.exports.output.publicPath = "/assets/"

module.exports.plugins.push(new Webpack.DefinePlugin({
  'process.env.NODE_ENV': JSON.stringify('production')
}));

////////////////////////////////////////
// clean
////////////////////////////////////////
module.exports.plugins.push(new CleanPlugin([ module.exports.output.path ]));

////////////////////////////////////////
// closure compiler
////////////////////////////////////////
module.exports.plugins.push(new ClosureCompilerPlugin({
  compiler: {
    language_in: 'ECMASCRIPT6',
    language_out: 'ECMASCRIPT5',
    compilation_level: 'SIMPLE', //TODO: ADVANCED
    process_common_js_modules: true,
    jscomp_off: 'checkVars',
    warning_level: 'QUIET'
  },
  concurrency: 3,
}));

////////////////////////////////////////
// html template generate index.html
////////////////////////////////////////
//TODO does not trigger when only changing html template file
module.exports.plugins.push(new HtmlPlugin({
    title: 'Woost',
    template: Path.join(dirs.assets, 'index.template.html'),
    favicon: 'icon.ico',
    minify: {
        // https://github.com/kangax/html-minifier#options-quick-reference
        removeComments: true,
        collapseWhitespace: true,
        removeAttributeQuotes: true
    },
}));

////////////////////////////////////////
// styles generated from scss
////////////////////////////////////////
//module.exports.entry[appName].push(Path.join(dirs.assets, "style.scss"));
cssFiles.forEach(function (file) {
    module.exports.entry[appName].push(file);
});
const extractSass = new ExtractTextPlugin({
    filename: filenamePattern + '.css'
});
module.exports.plugins.push(extractSass);
// module.exports.module.rules.push({
//     test: /style\.scss$/,
//     use: extractSass.extract({
//         use: [{ loader: "css-loader" }, { loader: "sass-loader" }],
//     })
// });
module.exports.module.rules.push({
    test: /\.css$/,
    use: extractSass.extract({
        use: [{ loader: "css-loader" }],
    })
});

////////////////////////////////////////
// sub resource integrity adds a hash to each loaded resource in the html
////////////////////////////////////////
module.exports.output.crossOriginLoading = 'anonymous';
module.exports.plugins.push(new SriPlugin({
    hashFuncNames: ['sha256']
}));

////////////////////////////////////////
// compression
////////////////////////////////////////
var compressFiles = /\.(js|js\.map|css|html|svg)$/;
module.exports.plugins.push(new ZopfliPlugin({
  asset: "[path].gz[query]",
  algorithm: "zopfli",
  test: compressFiles,
  minRatio: 0.0
}));
module.exports.plugins.push(new BrotliPlugin({
  asset: '[path].br[query]',
  test: compressFiles,
  minRatio: 0.0
}));

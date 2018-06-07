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

const commons = require('./webpack.base.common.js');
const dirs = commons.woost.dirs;
const appName = commons.woost.appName;
const cssFiles = commons.woost.cssFiles;
module.exports = commons.webpack;

// set up output path
module.exports.output.path = Path.join(__dirname, "dist");
// module.exports.output.publicPath = "/";

// copy some assets to dist folder
//TODO entry and handle with loader (hash)
function copyAssets(context) {
    return new CopyPlugin([
        { from: "**/*.ico", to: module.exports.output.path },
        { from: "**/*.svg", to: module.exports.output.path },
        { from: "**/*.png", to: module.exports.output.path },
        { from: "**/*.json", to: module.exports.output.path }
    ], { context: context });
}
module.exports.plugins.push(copyAssets(dirs.assets));

const filenamePattern = '[name].[chunkhash]';
module.exports.output.filename = filenamePattern + '.js';
// module.exports.output.publicPath = "/assets/"

module.exports.mode = 'production';

////////////////////////////////////////
// clean
////////////////////////////////////////
module.exports.plugins.push(new CleanPlugin([ module.exports.output.path ]));

////////////////////////////////////////
// closure compiler
////////////////////////////////////////
// https://github.com/google/closure-compiler-js#webpack
module.exports.optimization = {
    minimize: false // disable default uglifyJs
};
module.exports.plugins.push(new ClosureCompilerPlugin({
  compiler: {
    language_in: 'ECMASCRIPT6',
    language_out: 'ECMASCRIPT5',
    compilation_level: 'SIMPLE', //TODO: ADVANCED
    // process_common_js_modules: true,
    // jscomp_off: 'checkVars',
    warning_level: 'DEFAULT',
	create_source_map: true
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
    favicon: Path.join(dirs.assets, 'favicon.ico'),
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

module.exports.devtool = "source-map"; // activate sourcemaps in production
module.exports.module.rules.push({
    test: /\.js$/,
    use: ["source-map-loader"],
    enforce: "pre"
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
var compressFiles = /\.(js|map|css|html|svg)$/;
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

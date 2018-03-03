const Webpack = require('webpack');
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
module.exports = commons.webpack;

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
const extractSass = new ExtractTextPlugin({
    filename: filenamePattern + '.css'
});
module.exports.plugins.push(extractSass);
module.exports.module.rules.push({
    test: /style\.scss$/,
    use: extractSass.extract({
        use: [{ loader: "css-loader" }, { loader: "sass-loader" }],
    })
});
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

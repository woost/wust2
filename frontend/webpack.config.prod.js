const Webpack = require('webpack');
const ZopfliPlugin = require("zopfli-webpack-plugin");
const BrotliPlugin = require('brotli-webpack-plugin');
const ClosureCompilerPlugin = require("webpack-closure-compiler");

module.exports = require('./scalajs.webpack.config');

module.exports.plugins = module.exports.plugins || [];

// module.exports.plugins.push(new Webpack.optimize.UglifyJsPlugin({
//     compress: {
//         warnings: false
//     }
// }));
module.exports.plugins.push(new ClosureCompilerPlugin({
  compiler: {
    language_in: 'ECMASCRIPT6',
    language_out: 'ECMASCRIPT5',
    compilation_level: 'ADVANCED',
    process_common_js_modules: true,
    jscomp_off: 'checkVars',
    warning_level: 'QUIET'
  },
  concurrency: 3,
}));

module.exports.plugins.push(new Webpack.DefinePlugin({
  'process.env.NODE_ENV': JSON.stringify('production')
}));

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

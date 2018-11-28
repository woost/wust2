const Webpack = require('webpack');
const CopyPlugin = require("copy-webpack-plugin");
const CleanPlugin = require("clean-webpack-plugin");
const CompressionPlugin = require("compression-webpack-plugin");
const zopfli = require("@gfx/zopfli");
const BrotliPlugin = require('brotli-webpack-plugin');
const ClosureCompilerPlugin = require("webpack-closure-compiler");
// const SriPlugin = require("webpack-subresource-integrity");
const HtmlPlugin = require("html-webpack-plugin");
const HtmlIncludeAssetsPlugin = require("html-webpack-include-assets-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const Path = require('path');
const OptimizeCssAssetsPlugin = require('optimize-css-assets-webpack-plugin');

// before doing anything, we run the cssJVM project, which generates a css file for all scalacss styles into: webApp/src/css/scalacss.css
// this file will automatically be picked up by webpack from that folder.
const { execSync } = require('child_process');
// stderr is sent to stdout of parent process
// you can set options.stdio if you want it to go elsewhere
const rootFolder = Path.resolve(__dirname, '../../../../..');
process.env._JAVA_OPTIONS = "-Xms128M -Xmx500M";
execSync('cd ' + rootFolder + '; sbt cssJVM/run');


const commons = require('./webpack.base.common.js');
const dirs = commons.woost.dirs;
const appName = commons.woost.appName;
const cssFiles = commons.woost.cssFiles;
const htmlTemplateFile = commons.woost.htmlTemplateFile;
const staticIncludeAssets = commons.woost.staticIncludeAssets;
const staticCopyAssets = commons.woost.staticCopyAssets;
const versionString = commons.woost.versionString;
const gitBranch = execSync('(git symbolic-ref --short HEAD --quiet || git rev-parse HEAD || echo "") 2> /dev/null').toString().trim() // branch, fallback to commit hash
module.exports = commons.webpack;

// set up output path
module.exports.output.path = Path.join(__dirname, "dist");
// module.exports.output.publicPath = "/";

// copy some assets to dist folder
//TODO entry and handle with loader (hash)
function copyAssets(context) {
    var patterns = [{ from: "**/*", to: module.exports.output.path }];
    if(gitBranch !== 'production') {
        patterns.push({ from: "staging.webmanifest", to: Path.join(module.exports.output.path, "site.webmanifest") });
    }

    return new CopyPlugin(patterns, { context: context });
}
module.exports.plugins.push(copyAssets(dirs.assets));
module.exports.plugins.push(new CopyPlugin(staticCopyAssets));

// file name pattern for outputs with hash
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

process.env._JAVA_OPTIONS = "-Xms128M -Xmx500M";
module.exports.plugins.push(new ClosureCompilerPlugin({
  compiler: {
    language_in: 'ECMASCRIPT6',
    language_out: 'ECMASCRIPT5',
    compilation_level: 'SIMPLE', //TODO: ADVANCED
    // process_common_js_modules: true,
    // jscomp_off: 'checkVars',
    warning_level: 'DEFAULT',
    create_source_map: (process.env.SOURCEMAPS == 'true')
  },
  concurrency: 1
}));

////////////////////////////////////////
// html template generate index.html
////////////////////////////////////////
//TODO does not trigger when only changing html template file
module.exports.plugins.push(new HtmlPlugin({
    versionString: versionString,
    title: 'Woost',
    template: htmlTemplateFile,
    favicon: Path.join(dirs.assets, 'favicon.ico'),
    minify: {
        // https://github.com/kangax/html-minifier#options-quick-reference
        removeComments: true,
        collapseWhitespace: true,
        removeAttributeQuotes: true
    },
}));

module.exports.plugins.push(new HtmlIncludeAssetsPlugin({
    assets: staticIncludeAssets,
    append: false
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
module.exports.plugins.push(
    new OptimizeCssAssetsPlugin({
      assetNameRegExp: /\.css$/g,
      cssProcessor: require('cssnano'),
      cssProcessorPluginOptions: {
        preset: ['default', { discardComments: { removeAll: true } }],
      },
      canPrint: true
    })
);
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

if(process.env.SOURCEMAPS == 'true') {
    module.exports.devtool = "source-map"; // activate sourcemaps in production

    module.exports.module.rules.push({
        test: /\.js$/,
        use: ["source-map-loader"],
        enforce: "pre"
    });
}

////////////////////////////////////////
// sub resource integrity adds a hash to each loaded resource in the html
////////////////////////////////////////
// disabled because it can fail when we update non-fingerprinted assets
// module.exports.output.crossOriginLoading = 'anonymous';
// module.exports.plugins.push(new SriPlugin({
//     hashFuncNames: ['sha256']
// }));

////////////////////////////////////////
// compression
////////////////////////////////////////
var compressFiles = /\.(js|map|css|html|svg)$/;
module.exports.plugins.push(new CompressionPlugin({
  test: compressFiles,
  filename: "[path].gz[query]",
  compressionOptions: {
  },
  algorithm(input, compressionOptions, callback) {
      return zopfli.gzip(input, compressionOptions, callback);
  },
  minRatio: 1.0, // always compress
}));
module.exports.plugins.push(new BrotliPlugin({
  test: compressFiles,
  asset: '[path].br[query]',
  minRatio: 1.0, // always compress
}));

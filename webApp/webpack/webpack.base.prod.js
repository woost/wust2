const Webpack = require('webpack');
const CleanPlugin = require("clean-webpack-plugin");
const ClosureCompilerPlugin = require("webpack-closure-compiler");
const HtmlPlugin = require("html-webpack-plugin");
const ExtractTextPlugin = require("extract-text-webpack-plugin");
const OptimizeCssAssetsPlugin = require('optimize-css-assets-webpack-plugin');
const ConcatPlugin = require('webpack-concat-plugin');
const CopyPlugin = require("copy-webpack-plugin");
const HtmlAssetsPlugin = require("html-webpack-include-assets-plugin");
const uglifyJs = require("uglify-js")
const Path = require('path');
const { execSync } = require('child_process');

// const SriPlugin = require("webpack-subresource-integrity");
// const CompressionPlugin = require("compression-webpack-plugin");
// const zopfli = require("@gfx/zopfli");
// const BrotliPlugin = require('brotli-webpack-plugin');

//[hash] - Returns the build hash. If any portion of the build changes, this changes as well.
//[chunkhash] - Returns an entry chunk-specific hash. Each entry defined in the configuration receives a hash of its own. If any portion of the entry changes, the hash will change as well. [chunkhash] is more granular than [hash] by definition.
//[contenthash] - Returns a hash generated based on content.

////////////////////////////////////////
// First step: generate css files from scalacss
////////////////////////////////////////
// before doing anything, we run the cssJVM project, which generates a css file for all scalacss styles into: webApp/src/css/scalacss.css
// this file will automatically be picked up by webpack from that folder.
process.env._JAVA_OPTIONS = "-Xmx2G";
execSync('cd ' + Path.resolve(__dirname, '../../../../..') + '; sbt cssJVM/run');

const commons = require('./webpack.base.common.js');
const woost = commons.woost;
const outputFileNamePattern = '[name].[chunkhash]';

module.exports = commons.webpack;
module.exports.mode = 'production';
module.exports.output.path = Path.join(__dirname, "dist");
module.exports.output.filename = outputFileNamePattern + '.js';

woost.files.css.forEach(file => module.exports.entry[woost.appName].push(file));
module.exports.entry[woost.appName] = module.exports.entry[woost.appName].concat(woost.files.vendor.assets).concat(woost.files.assets);
module.exports.optimization = {
    // https://github.com/google/closure-compiler-js#webpack
    minimize: false, // disable default uglifyJs

    splitChunks: {
        cacheGroups: {
            dependencies: {
                test: /[\\/]node_modules[\\/]/,
                name: 'dependencies',
                chunks: 'all'
            }
        }
    }
};

////////////////////////////////////////
// clean
////////////////////////////////////////
if (!process.env.WUST_PROD_DEVELOPMENT) {
    module.exports.plugins.push(new CleanPlugin([ module.exports.output.path ]));
}

////////////////////////////////////////
// closure compiler
////////////////////////////////////////
process.env._JAVA_OPTIONS = "-Xmx2G";
module.exports.plugins.push(new ClosureCompilerPlugin({
  compiler: {
    language_in: 'ECMASCRIPT_2015',
    language_out: 'ECMASCRIPT_2015',
    compilation_level: 'SIMPLE', //TODO: ADVANCED
    // process_common_js_modules: true,
    // jscomp_off: 'checkVars',
    warning_level: 'DEFAULT',
    create_source_map: (process.env.SOURCEMAPS == 'true')
  },
  concurrency: 1
}));

////////////////////////////////////////
// html template generate html files
////////////////////////////////////////
woost.files.html.forEach(htmlFile => {
    const addIndexHtml = function(audience, filename) {
        module.exports.plugins.push(new HtmlPlugin({
            templateParameters: woost.templateParametersFunction({
                title: "Woost",
                audience: audience,
            }),
            filename: filename,
            template: htmlFile,
            chunks: ["dependencies", woost.appName],
            chunksSortMode: 'manual',
            minify: minifyOpts
        }));
    }
    const addOtherHtml = function() {
        module.exports.plugins.push(new HtmlPlugin({
            filename: Path.basename(htmlFile),
            template: htmlFile,
            chunks: [],
            minify: minifyOpts,
            inject: false
        }));
    }
    const isIndexHtml = Path.basename(htmlFile) == "index.html";
    const minifyOpts = {
        // https://github.com/kangax/html-minifier#options-quick-reference
        removeComments: true,
        collapseWhitespace: true,
        removeAttributeQuotes: true
    };

    if (isIndexHtml) {
        // for index html we generate two files, one for staging, one for app. with a staging audience to enable certain features.
        addIndexHtml("app", "index.html");
        addIndexHtml("staging", "staging.html");
    } else {
        addOtherHtml();
    }
});

////////////////////////////////////////
// merge vendor js files into one file
////////////////////////////////////////
module.exports.plugins.push(new ConcatPlugin({
    uglify: true,
    sourceMap: false,
    injectType: 'prepend',
    name: 'external',
    fileName: '[name].[hash].js',
    filesToConcat: woost.files.vendor.js
}));

////////////////////////////////////////
// merge sw files into one file
////////////////////////////////////////
module.exports.plugins.push(new ConcatPlugin({
    uglify: true,
    sourceMap: false,
    injectType: 'none',
    name: 'sw',
    fileName: '[name].[hash].js',
    filesToConcat: woost.files.sw
}));

////////////////////////////////////////
// bundle css files
////////////////////////////////////////
const extractCss = new ExtractTextPlugin({ filename: outputFileNamePattern + '.css' });
module.exports.plugins.push(extractCss);
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
//     use: extractCss.extract({
//         use: [{ loader: "css-loader" }, { loader: "sass-loader" }],
//     })
// });
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
const fileLoader = {
    loader: 'file-loader',
    options: {
        name: 'assets/[name].[contenthash].[ext]',
    },
};
module.exports.module.rules.push({
    test: /static\/\.(png|jpe?g|ico|svg|gif|woff2?|ttf|eot)$/,
    use: [ fileLoader ]
});
module.exports.module.rules.push({
    test: /\.(png|jpe?g|ico|svg|gif|woff2?|ttf|eot)$/,
    use: [ fileLoader ]
});
module.exports.module.rules.push({
      test: /(\.webmanifest|browserconfig\.xml)$/,
      use: [
        fileLoader,
        {
            loader: "app-manifest-loader",
            options: {
                publicPath: "/"
            }
        }
      ]
});

////////////////////////////////////////
// Generate Sourcemaps
////////////////////////////////////////
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
// pre-compression of assets with brotli/zopfli
////////////////////////////////////////
// var compressFiles = /\.(js|map|css|html|svg)$/;
// module.exports.plugins.push(new CompressionPlugin({
//   test: compressFiles,
//   filename: "[path].gz[query]",
//   compressionOptions: {
//   },
//   algorithm(input, compressionOptions, callback) {
//       return zopfli.gzip(input, compressionOptions, callback);
//   },
//   minRatio: 1.0, // always compress
// }));
// module.exports.plugins.push(new BrotliPlugin({
//   test: compressFiles,
//   asset: '[path].br[query]',
//   minRatio: 1.0, // always compress
// }));

////////////////////////////////////////
// dev server
////////////////////////////////////////
if (process.env.WUST_PROD_DEVELOPMENT) {
    module.exports.devServer = {
        // https://webpack.js.org/configuration/dev-server
        port: process.env.WUST_PORT,
        contentBase: [
            module.exports.output.path,
            woost.dirs.root // serve complete project for providing source-maps, needs to be ignored for watching
        ],
        watchContentBase: false,
        open: false, // open page in browser
        hot: false,
        hotOnly: false, // only reload when build is sucessful
        inline: false, // live reloading
        overlay: false, // this breaks the compiled app-fastopt-library.js
        host: "0.0.0.0", //TODO this is needed so that in dev docker containers can access devserver through docker bridge
        allowedHosts: [ ".localhost" ],

        //proxy websocket requests to app
        proxy : [
            woost.setupDevServerProxy({ /*subdomain: "core", */path: "ws", port: process.env.WUST_CORE_PORT, ws: true }),
            woost.setupDevServerProxy({ /*subdomain: "core", */path: "api", port: process.env.WUST_CORE_PORT }),
        ],
        compress: (process.env.DEV_SERVER_COMPRESS == 'true')
    };
    // module.exports.plugins.push(new Webpack.HotModuleReplacementPlugin())
}

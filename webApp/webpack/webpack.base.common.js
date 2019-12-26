const glob = require("glob");
const Path = require("path");
const fs = require("fs");
const { execSync } = require('child_process');

const workboxVersion = "v4.3.1"
const workboxUrl = `https://github.com/GoogleChrome/workbox/releases/download/${workboxVersion}/workbox-${workboxVersion}.tar.gz`

// directories
const dirs = {}
dirs.project = Path.resolve(__dirname, '../../../..');
dirs.root = Path.resolve(dirs.project, '..');
dirs.assets = Path.join(dirs.project, 'assets');
dirs.workbox = Path.join(dirs.project, `workbox-${workboxVersion}`);
dirs.css = Path.join(dirs.project, 'src/css');
dirs.html = Path.join(dirs.project, 'src/html');
dirs.sw = Path.join(dirs.project, 'src/sw');

// download workbox if not exists
execSync(`[ ! -d ${dirs.workbox} ] && mkdir -p ${dirs.workbox} && wget ${workboxUrl} -O workbox.tar.gz && tar xvzf workbox.tar.gz -C ${dirs.workbox} || true`)

// initialize module exports
const webpack = require(Path.join(__dirname, 'scalajs.webpack.config'));
const appName = Object.keys(webpack.entry)[0];
webpack.plugins = webpack.plugins || [];
webpack.module = webpack.module || {};
webpack.module.rules = webpack.module.rules || [];

// TODO bug with custom output in https://github.com/scalacenter/scalajs-bundler/issues/192
// expects the originally configured output file to exist, just create it.
const dummyOutputFile = Path.join(webpack.output.path, webpack.output.filename.replace('[name]', appName));
if (!fs.existsSync(dummyOutputFile)) {
    fs.closeSync(fs.openSync(dummyOutputFile, 'w'));
}

// gather resources
const files = {}
files.css = glob.sync(Path.join(dirs.css, '**', '*.css'));
files.html = glob.sync(Path.join(dirs.html, '**', '*.html'))
files.assets = glob.sync(Path.join(dirs.assets, '**', '*.*')) // favicons, webmanifests, browserconfig, images, ...
files.sw = glob.sync(Path.join(dirs.sw, '**', '*.*')) // all files related to service worker

// files that we do not require with require-imports, but need to be in global scope.
files.vendor = {
    assets: [
        Path.join(__dirname, 'node_modules/emoji-datasource-twitter/img/twitter/sheets/64.png'),
        Path.join(__dirname, 'node_modules/fomantic-ui-css/semantic.css'),
        Path.join(__dirname, 'node_modules/highlight.js/styles/github-gist.css'),
        Path.join(__dirname, 'node_modules/wdt-emoji-bundle/wdt-emoji-bundle.css'),
        Path.join(__dirname, 'node_modules/wdt-emoji-bundle/sheets/sheet_twitter_64_indexed_128.png'),
        Path.join(__dirname, 'node_modules/tributejs/dist/tribute.css'),
        Path.join(__dirname, 'node_modules/hopscotch/dist/css/hopscotch.min.css'),
        Path.join(__dirname, 'node_modules/hopscotch/dist/img/sprite-green.png'),
        Path.join(__dirname, 'node_modules/hopscotch/dist/img/sprite-orange.png'),
        Path.join(__dirname, 'node_modules/flatpickr/dist/flatpickr.css'),
        Path.join(__dirname, 'node_modules/tippy.js/dist/tippy.css'),
        Path.join(__dirname, 'node_modules/tippy.js/dist/backdrop.css'),
        Path.join(__dirname, 'node_modules/tippy.js/animations/shift-away.css'),
        // Path.join(__dirname, 'node_modules/tippy.js/themes/light.css'),
        Path.join(__dirname, 'node_modules/tippy.js/themes/light-border.css'),
        // Path.join(__dirname, 'node_modules/tippy.js/themes/material.css'),
    ],
    js: [
        Path.join(__dirname, 'node_modules/jquery/dist/jquery.js'),
        Path.join(__dirname, 'node_modules/jquery-tablesort/jquery.tablesort.js'),
        Path.join(__dirname, 'node_modules/clipboard/dist/clipboard.js'),
        Path.join(__dirname, 'node_modules/emoji-js/lib/emoji.js'),
        Path.join(__dirname, 'node_modules/fomantic-ui-css/semantic.js'),
        Path.join(__dirname, 'node_modules/wdt-emoji-bundle/emoji.js'),
        Path.join(__dirname, 'node_modules/wdt-emoji-bundle/wdt-emoji-bundle.js'),
        Path.join(__dirname, 'node_modules/hopscotch/dist/js/hopscotch.min.js'),
        Path.join(__dirname, 'node_modules/setimmediate/setImmediate.js'),
    ],
    workbox: glob.sync(Path.join(dirs.workbox, '**', '*')) // workbox is required by serviceworker and there is no real dependency to require...
};



const woostVersion = process.env.WUST_VERSION ? process.env.WUST_VERSION : "latest";

const templateParametersFunction = templateParameters => (compilation, assets, assetTags, options) => {
    return {
        ...templateParameters,
        versionString: woostVersion,
        webpack: { compilation, assets, assetTags, options }
    };
};

function setupDevServerProxy(config) {
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

// export
module.exports.webpack = webpack;
module.exports.woost = {
    appName, dirs, files, templateParametersFunction, setupDevServerProxy
};


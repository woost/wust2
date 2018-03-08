const CopyPlugin = require("copy-webpack-plugin");
const fs = require("fs");
const glob = require("glob");
const Path = require("path");

// directories
const dirs = {}
dirs.project = Path.resolve(__dirname, '../../../..');
dirs.root = Path.resolve(dirs.project, '..');
dirs.assets = Path.join(dirs.project, 'assets');
dirs.projectRoot = Path.join(dirs.root, 'utilWeb', 'project-root'); // project-root has symlinks to projects in root folder

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

// set up output path
webpack.output.path = Path.join(__dirname, "dist");
// webpack.output.publicPath = "/";
//webpack.entry[appName].push(Path.join(dirs.assets, "style.scss"));
const cssFiles = glob.sync(Path.join(dirs.assets, "*.css")).concat(glob.sync(Path.join(__dirname, "*.css")));
cssFiles.forEach(function (file) {
    webpack.entry[appName].push(file);
});

// copy some assets to dist folder
webpack.plugins.push(new CopyPlugin([
    { from: '*.ico', to: webpack.output.path },
    { from: '*.svg', to: webpack.output.path },
    { from: '*.png', to: webpack.output.path },
    { from: 'manifest.json', to: webpack.output.path }
]));

// export
module.exports.webpack = webpack;
module.exports.woost = {
    appName: appName,
    dirs: dirs
};

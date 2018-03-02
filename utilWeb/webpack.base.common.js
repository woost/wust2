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
webpack.plugins = webpack.plugins || [];
webpack.module = webpack.module || {};
webpack.module.rules = webpack.module.rules || [];

// TODO bug with custom output in https://github.com/scalacenter/scalajs-bundler/issues/192
// expects the originally configured output file to exist, just create it.
const appName = Object.keys(webpack.entry)[0];
fs.closeSync(fs.openSync(Path.join(webpack.output.path, appName + '-bundle.js'), 'w'));

// set up output path
webpack.output.path = Path.join(__dirname, "dist");
webpack.entry[appName].push(Path.join(dirs.assets, "style.scss"));
const cssFiles = glob.sync(Path.join(dirs.assets, "*.css"));
cssFiles.forEach(function (file) {
    webpack.entry[appName].push(file);
});

// export
module.exports.webpack = webpack;
module.exports.woost = {
    appName: appName,
    dirs: dirs
};

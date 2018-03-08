const CopyPlugin = require("copy-webpack-plugin");
const glob = require("glob");
const Path = require("path");

// directories
const dirs = {}
dirs.project = Path.resolve(__dirname, '../../../..');
dirs.root = Path.resolve(dirs.project, '..');
dirs.assets = Path.join(dirs.project, 'assets');
dirs.sharedAssets = Path.join(dirs.root, 'utilWeb', 'assets');
dirs.projectRoot = Path.join(dirs.root, 'utilWeb', 'project-root'); // project-root has symlinks to projects in root folder

// initialize module exports
const webpack = require(Path.join(__dirname, 'scalajs.webpack.config'));
const appName = Object.keys(webpack.entry)[0];
webpack.plugins = webpack.plugins || [];
webpack.module = webpack.module || {};
webpack.module.rules = webpack.module.rules || [];

// gather css resources
const cssFiles = glob.sync(Path.join(dirs.assets, "*.css")).concat(glob.sync(Path.join(dirs.sharedAssets, "*.css")));

// export
module.exports.webpack = webpack;
module.exports.woost = {
    appName: appName,
    dirs: dirs,
    cssFiles: cssFiles
};

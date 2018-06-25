const CopyPlugin = require("copy-webpack-plugin");
const glob = require("glob");
const Path = require("path");
const fs = require("fs");

// directories
const dirs = {}
dirs.project = Path.resolve(__dirname, '../../../..');
dirs.root = Path.resolve(dirs.project, '..');
dirs.assets = Path.join(dirs.project, 'assets');

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
const cssFiles = glob.sync(Path.join(dirs.project, 'src/css/*.css'));
const htmlTemplateFile = Path.join(dirs.project, 'src/html/index.template.html');

// export
module.exports.webpack = webpack;
module.exports.woost = {
    appName: appName,
    dirs: dirs,
    cssFiles: cssFiles,
    htmlTemplateFile: htmlTemplateFile
};

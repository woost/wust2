const Webpack = require('webpack');
const Path = require('path');

const rootDir = Path.resolve(__dirname, '../../../../..');


module.exports = require('./scalajs.webpack.config');

// https://webpack.js.org/configuration/dev-server
module.exports.devServer = {
    contentBase: [
        // __dirname,
        Path.resolve(__dirname, 'fastopt'), // fastOptJS output
        Path.resolve(rootDir, 'assets/public'), // css files, ...
        Path.resolve(rootDir, 'assets/dev') // development index.html
    ],
    watchContentBase: true,
    // watchOptions: { poll: true },
    open: true, // open page in browser
    hot: true
};

module.exports.plugins = [
    new Webpack.HotModuleReplacementPlugin()
];




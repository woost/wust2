const Webpack = require('webpack');
const Path = require('path');

const rootDir = Path.resolve(__dirname, '../../../../..');


module.exports = require('./scalajs.webpack.config');

const projectRoot = Path.resolve(rootDir, 'assets/project-root'); // assets/sources is a symlink to the root folder

// https://webpack.js.org/configuration/dev-server
module.exports.devServer = {
    contentBase: [
        // __dirname,
        Path.resolve(__dirname, 'fastopt'), // fastOptJS output
        Path.resolve(rootDir, 'assets/public'), // css files, ...
        Path.resolve(rootDir, 'assets/dev'), // development index.html
        projectRoot, // serve project files for source-map access
    ],
    watchContentBase: true,
    watchOptions: {
        ignored: projectRoot
    },
    // watchOptions: { poll: true },
    open: false, // open page in browser
    hot: true,

    //proxy websocket requests to app
    proxy : [
        {
            path: '/ws',
            target: 'ws://localhost:8080/',
            ws: true
        }
    ]
    // ,compress: true
};

module.exports.plugins = [
    new Webpack.HotModuleReplacementPlugin()
];




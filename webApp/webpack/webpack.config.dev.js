const merge = require('webpack-merge');

module.exports = merge(
    require('./webpack.base.dev.js'),
    // require('./webpack.base.offline.js')
);

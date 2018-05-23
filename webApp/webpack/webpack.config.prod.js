const merge = require('webpack-merge');

module.exports = merge(
    require('./webpack.base.prod.js'),
    require('./webpack.base.offline.js')
);

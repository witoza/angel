var webpack = require("webpack");

const path = require('path');
const CleanWebpackPlugin = require('clean-webpack-plugin');

module.exports = {

    devtool: 'cheap-module-source-map',

    entry: {
        vendor: "./app/vendor.module.js",
        app: "./app/app.module.js"
    },

    output: {
        path: path.resolve(__dirname + "/app", 'dist'),
        filename: '[name].bundle.js',

    },

    plugins: [
        new CleanWebpackPlugin(['app/dist'])
    ],

    devServer: {
        port: 7777,
        host: 'localhost',
        historyApiFallback: true,
        noInfo: false,
        stats: 'minimal'
    }

}
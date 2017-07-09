var webpack = require("webpack");

const path = require('path');
const CleanWebpackPlugin = require('clean-webpack-plugin');
const ngAnnotatePlugin = require('ng-annotate-webpack-plugin');

module.exports = {

    entry: {
        vendor: "./app/vendor.module.js",
        app: "./app/app.module.js"
    },


    output: {
        path: path.resolve(__dirname + "/app", 'dist'),
        filename: '[name].bundle.js'

    },

    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: /(node_modules|bower_components)/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        presets: ['env']
                    }
                }
            }
        ]
    },

    plugins: [
        new CleanWebpackPlugin(['app/dist']),
        new ngAnnotatePlugin({
            add: true,
            // other ng-annotate options here 
        }),
        new webpack.optimize.UglifyJsPlugin({
            mangle: true,
            compress: {
                drop_console: true
            }
        })

    ]
}
/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Open Source Version is distributed with Sencha Ext JS pursuant to a FLOSS Exception agreed upon
 * between Sonatype, Inc. and Sencha Inc. Sencha Ext JS is licensed under GPL v3 and cannot be redistributed as part of a
 * closed source work.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
const { rspack } = require('@rspack/core');
const path = require('path');
const fs = require('fs');

const swcOptions = JSON.parse(fs.readFileSync(path.resolve(__dirname, '.swcrc')), 'utf-8');

module.exports = {
  entry: {
    'nexus-coreui-bundle': './src/frontend/src/index.js'
  },
  module: {
    rules: [
      {
        test: /\.jsx?$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'builtin:swc-loader',
            options: swcOptions
          }
        ]
      },
      {
        test: /\.js$/,
        include: /node_modules[\/\\]fuse\.js/,
        use: [
          {
            loader: 'builtin:swc-loader'
          }
        ]
      },
      {
        test: /\.s?css$/,
        use: [
          {
            loader: rspack.CssExtractRspackPlugin.loader
          },
          {
            loader: 'css-loader',
            options: { url: false } // disable build-tile resolution of url() paths
          },
          {
            loader: 'sass-loader'
          }
        ]
      },
      {
        test: /\.(png)$/,
        type: 'asset',
        generator: {
          filename: 'img/[name][ext]'
        }
      },
      {
        test: /\.(ttf|eot|woff2?|svg)$/,
        type: 'asset/resource',
        generator: {
          filename: 'fonts/[name][ext]'
        }
      }
    ]
  },
  plugins: [
    new rspack.CssExtractRspackPlugin({
      filename: '[name].css',
      chunkFilename: '[name].[contenthash:8].css'
    }),
    new rspack.CopyRspackPlugin({
      patterns: [{
        from: path.resolve(__dirname, '../../../../node_modules/@sonatype/react-shared-components/assets/'),
        to: path.resolve(__dirname, 'target/classes/assets')
      }]
    })
  ],
  resolve: {
    extensions: ['.js', '.jsx']
  },
  externals: {
    axios: 'axios',
    luxon: 'luxon',
    '@sonatype/react-shared-components': 'rsc',
    react: 'react',
    xstate: 'xstate'
  }
};

import { defineConfig } from '@tarojs/cli';

export default defineConfig({
  projectName: 'icedmall-miniprogram',
  date: '2026-6-28',
  designWidth: 375,
  deviceRatio: {
    375: 2 / 1,
    640: 2 / 2.34,
    750: 1,
    828: 2 / 1.81,
  },
  sourceRoot: 'src',
  outputRoot: 'dist',
  plugins: [
    '@tarojs/plugin-platform-weapp',
    '@tarojs/plugin-platform-alipay',
    '@tarojs/plugin-framework-react',
  ],
  defineConstants: {},
  copy: {
    patterns: [],
    options: {},
  },
  framework: 'react',
  compiler: 'webpack5',
  mini: {
    postcss: {
      pxtransform: {
        enable: true,
        config: {},
      },
      cssModules: {
        enable: false,
        config: {
          namingPattern: 'module',
          generateScopedName: '[name]__[local]___[hash:base64:5]',
        },
      },
    },
  },
  h5: {
    publicPath: '/',
    staticDirectory: 'static',
  },
});

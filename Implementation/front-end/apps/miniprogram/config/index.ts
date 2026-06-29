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
  compilerOptions: {
    webpackChain(chain) {
      // 确保 React 不会跨 chunk 分割（避免 __SECRET_INTERNALS 引用断裂）
      chain.optimization.splitChunks({
        chunks: 'all',
        cacheGroups: {
          react: {
            name: 'vendors',
            test: /[\\/]node_modules[\\/](react|react-dom|scheduler)[\\/]/,
            priority: 20,
            chunks: 'all',
          },
        },
      });
      // 确保所有文件解析到同一个 react 实例
      chain.resolve.alias.set('react', require.resolve('react'));
    },
  },
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

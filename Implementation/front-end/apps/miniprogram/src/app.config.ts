/**
 * Taro App 配置
 * 定义页面路由 + 窗口样式 + 底部导航
 */
export default {
  pages: [
    'pages/index/index',
    'pages/marketplace/index',
    'pages/cart/index',
    'pages/profile/index',
  ],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: '#F7F6F3',
    navigationBarTitleText: '冰美商城',
    navigationBarTextStyle: 'black',
    backgroundColor: '#F7F6F3',
  },
  tabBar: {
    color: '#8C8A87',
    selectedColor: '#4A7C59',
    backgroundColor: '#FFFFFF',
    borderStyle: 'white',
    list: [
      { pagePath: 'pages/index/index', text: '首页' },
      { pagePath: 'pages/marketplace/index', text: '商城' },
      { pagePath: 'pages/cart/index', text: '购物车' },
      { pagePath: 'pages/profile/index', text: '我的' },
    ],
  },
};

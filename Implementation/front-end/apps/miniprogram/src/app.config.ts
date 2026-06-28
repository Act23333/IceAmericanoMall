/**
 * Taro App 配置
 * 定义页面路由 + 窗口样式 + 底部导航
 */
export default {
  pages: [
    'pages/index/index',
    'pages/marketplace/marketplace',
    'pages/cart/cart',
    'pages/profile/profile',
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
      {
        pagePath: 'pages/index/index',
        text: '首页',
        iconPath: 'assets/icons/home.png',
        selectedIconPath: 'assets/icons/home-active.png',
      },
      {
        pagePath: 'pages/marketplace/marketplace',
        text: '商城',
        iconPath: 'assets/icons/shop.png',
        selectedIconPath: 'assets/icons/shop-active.png',
      },
      {
        pagePath: 'pages/cart/cart',
        text: '购物车',
        iconPath: 'assets/icons/cart.png',
        selectedIconPath: 'assets/icons/cart-active.png',
      },
      {
        pagePath: 'pages/profile/profile',
        text: '我的',
        iconPath: 'assets/icons/user.png',
        selectedIconPath: 'assets/icons/user-active.png',
      },
    ],
  },
};

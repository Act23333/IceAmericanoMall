/**
 * 冰美商城 — 小程序 App 入口
 *
 * Taro 4 + React 19
 */
import { useLaunch } from '@tarojs/taro';
import { PropsWithChildren } from 'react';
import './app.scss';

function App({ children }: PropsWithChildren) {
  useLaunch(() => {
    console.log('[冰美商城] 小程序启动');
  });

  return children;
}

export default App;

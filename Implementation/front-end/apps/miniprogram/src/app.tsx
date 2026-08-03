import { useLaunch } from '@tarojs/taro';
import { PropsWithChildren } from 'react';
import './app.scss';

export default function App({ children }: PropsWithChildren) {
  useLaunch(() => {
    console.log('[冰美商城] 小程序启动');
  });
  return children;
}

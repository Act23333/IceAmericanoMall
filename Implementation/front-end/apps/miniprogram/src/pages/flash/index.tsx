import { View, Text } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState, useEffect } from 'react';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

export default function FlashPage() {
  const [list, setList] = useState<any[]>([]);
  const [now, setNow] = useState(Date.now());

  useLoad(() => { fetchList(); });

  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, []);

  async function fetchList() {
    try { const data = await apiClient<any[]>('/api/flash'); setList(data || []); } catch {}
  }

  async function buy(flashId: number) {
    try {
      const result = await apiClient<any>(`/api/flash/buy?flashId=${flashId}&addressId=1`, { method: 'POST' });
      Taro.showToast({ title: '抢购成功', icon: 'success' });
      if (result?.orderNo) Taro.navigateTo({ url: `/pages/pay/index?orderNo=${result.orderNo}` });
    } catch (e: any) { Taro.showToast({ title: e?.message || '抢购失败', icon: 'none' }); }
  }

  return (
    <View className="page">
      <Text className="page-title">⚡ 限时秒杀</Text>
      {list.map((fs: any) => {
        const endMs = new Date(fs.endTime).getTime();
        const remain = Math.max(0, endMs - now);
        const m = Math.floor(remain / 60000);
        const s = Math.floor((remain % 60000) / 1000);
        const progress = fs.stock > 0 ? Math.round((fs.soldCount || 0) / (fs.stock + (fs.soldCount || 0)) * 100) : 0;
        return (
          <View key={fs.id} className="flash-card">
            <View className="flash-header">
              <Text className="flash-countdown">⏱ {m}:{String(s).padStart(2, '0')}</Text>
              <Text className="flash-progress">已抢 {progress}%</Text>
            </View>
            <View className="flash-body">
              <View className="flash-info">
                <Text className="flash-product">{fs.productName || '秒杀商品'}</Text>
                <View className="flash-prices">
                  <Text className="flash-price">¥{formatPrice(fs.flashPrice || fs.price)}</Text>
                  {fs.flashPrice && fs.price && fs.flashPrice < fs.price && (
                    <Text className="flash-original">¥{formatPrice(fs.price)}</Text>
                  )}
                </View>
              </View>
              <View className="flash-btn" onClick={() => buy(fs.id)}><Text>抢购</Text></View>
            </View>
          </View>
        );
      })}
    </View>
  );
}

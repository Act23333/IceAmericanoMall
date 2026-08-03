import { View, Text } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

export default function CouponsPage() {
  const [templates, setTemplates] = useState<any[]>([]);
  const [myCoupons, setMyCoupons] = useState<any[]>([]);
  const [tab, setTab] = useState<'available' | 'template'>('available');

  useDidShow(() => {
    fetchMyCoupons();
    fetchTemplates();
  });

  async function fetchMyCoupons() {
    try { const data = await apiClient<any[]>('/api/coupon/available'); setMyCoupons(data || []); } catch {}
  }

  async function fetchTemplates() {
    try { const data = await apiClient<any[]>('/api/coupon/template'); setTemplates(data || []); } catch {}
  }

  async function claim(couponId: string) {
    try {
      await apiClient(`/api/coupon/claim?couponId=${couponId}`, { method: 'POST' });
      Taro.showToast({ title: '领取成功', icon: 'success' });
      fetchMyCoupons();
    } catch (e: any) { Taro.showToast({ title: e?.message || '领取失败', icon: 'none' }); }
  }

  return (
    <View className="page">
      <Text className="page-title">优惠券</Text>
      <View className="tab-row">
        <Text className={`tab ${tab === 'available' ? 'active' : ''}`} onClick={() => setTab('available')}>我的 ({myCoupons.length})</Text>
        <Text className={`tab ${tab === 'template' ? 'active' : ''}`} onClick={() => setTab('template')}>领券中心</Text>
      </View>

      {tab === 'available' ? (
        myCoupons.length === 0 ? <View className="empty"><Text>暂无优惠券</Text></View> :
        myCoupons.map((c: any) => (
          <View key={c.id} className="coupon-card">
            <View className="coupon-left">
              <Text className="coupon-amount">¥{formatPrice(c.value || 0)}</Text>
            </View>
            <View className="coupon-right">
              <Text className="coupon-name">{c.name || '优惠券'}</Text>
              <Text className="coupon-desc">{c.minAmount > 0 ? `满¥${formatPrice(c.minAmount)}可用` : '无门槛'}</Text>
            </View>
          </View>
        ))
      ) : (
        templates.map((t: any) => (
          <View key={t.couponId || t.id} className="coupon-card">
            <View className="coupon-left">
              <Text className="coupon-amount">¥{formatPrice(t.priceInCents || t.value || 0)}</Text>
            </View>
            <View className="coupon-right">
              <Text className="coupon-name">{t.name}</Text>
              <Text className="coupon-desc">{t.minAmountInCents > 0 ? `满¥${formatPrice(t.minAmountInCents)}可用` : '无门槛'}</Text>
              <View className="claim-btn" onClick={() => claim(t.couponId || String(t.id))}><Text>立即领取</Text></View>
            </View>
          </View>
        ))
      )}
    </View>
  );
}

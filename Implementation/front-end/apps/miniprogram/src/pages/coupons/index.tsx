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
      await apiClient(`/api/coupon/claim?coupon_id=${couponId}`, { method: 'POST' });
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
          // ★ 字段更正：UserCouponVO = { id, coupon_id, status, used_order_no, use_time, create_time }
          //   —— 后端**不投影**券面（name/value/min_amount），故只能展示券号 + 状态
          <View key={c.id} className="coupon-card">
            <View className="coupon-left">
              <Text className="coupon-amount">券</Text>
            </View>
            <View className="coupon-right">
              <Text className="coupon-name">优惠券 #{c.coupon_id}</Text>
              <Text className="coupon-desc">{c.status === 0 ? '可用' : c.status === 1 ? '已使用' : '已过期'}</Text>
            </View>
          </View>
        ))
      ) : (
        templates.map((t: any) => (
          // ★ 字段更正：CouponVO = { coupon_id, name, type, value, min_amount, price_in_cents, ... }
          <View key={t.coupon_id} className="coupon-card">
            <View className="coupon-left">
              <Text className="coupon-amount">¥{formatPrice(t.price_in_cents || t.value || 0)}</Text>
            </View>
            <View className="coupon-right">
              <Text className="coupon-name">{t.name}</Text>
              <Text className="coupon-desc">{t.min_amount > 0 ? `满¥${formatPrice(t.min_amount)}可用` : '无门槛'}</Text>
              <View className="claim-btn" onClick={() => claim(t.coupon_id)}><Text>立即领取</Text></View>
            </View>
          </View>
        ))
      )}
    </View>
  );
}

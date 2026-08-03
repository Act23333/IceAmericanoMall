import { View, Text } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState, useEffect } from 'react';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

export default function PayPage() {
  const [order, setOrder] = useState<any>(null);
  const [channel, setChannel] = useState('WECHAT');
  const [payUrl, setPayUrl] = useState('');

  useLoad((options) => {
    if (options?.orderNo) fetchOrder(options.orderNo);
  });

  async function fetchOrder(orderNo: string) {
    try { const data = await apiClient<any>(`/api/trade/order/${orderNo}`); setOrder(data); } catch {}
  }

  async function doPay() {
    if (!order) return;
    try {
      const pay = await apiClient<any>(`/api/pay/order/${order.orderNo}?channel=${channel}`, { method: 'POST' });
      if (pay.status === 3) {
        Taro.showToast({ title: '支付成功', icon: 'success' });
        setTimeout(() => Taro.redirectTo({ url: `/pages/order-detail/index?orderNo=${order.orderNo}` }), 1000);
      } else if (pay.qrCodeUrl) {
        setPayUrl(pay.qrCodeUrl);
      }
    } catch (e: any) { Taro.showToast({ title: e?.message || '支付失败', icon: 'none' }); }
  }

  // Poll payment status
  useEffect(() => {
    if (!order || !payUrl) return;
    const t = setInterval(async () => {
      try {
        const p = await apiClient<any>(`/api/pay/order/${order.orderNo}/status`);
        if (p.status === 3) {
          clearInterval(t);
          Taro.showToast({ title: '支付成功', icon: 'success' });
          setTimeout(() => Taro.redirectTo({ url: `/pages/order-detail/index?orderNo=${order.orderNo}` }), 500);
        }
      } catch {}
    }, 3000);
    return () => clearInterval(t);
  }, [payUrl]);

  if (!order) return <View className="page"><Text>加载中...</Text></View>;

  return (
    <View className="page">
      <Text className="page-title">收银台</Text>
      <View className="pay-amount">
        <Text className="pay-label">应付金额</Text>
        <Text className="pay-price">¥{formatPrice(order.payAmount)}</Text>
      </View>

      <View className="channel-list">
        {[
          { key: 'WECHAT', label: '微信支付', icon: '💚' },
          { key: 'ALIPAY', label: '支付宝', icon: '💙' },
          { key: 'BALANCE', label: '余额支付', icon: '👛' },
        ].map((c) => (
          <View key={c.key} className={`channel-item ${channel === c.key ? 'selected' : ''}`} onClick={() => setChannel(c.key)}>
            <Text>{c.icon} {c.label}</Text>
            {channel === c.key && <Text>✓</Text>}
          </View>
        ))}
      </View>

      {payUrl && (
        <View className="pay-link">
          <Text className="pay-link-text">支付链接已生成 (演示环境)</Text>
          <Text className="pay-link-url" onClick={() => Taro.setClipboardData({ data: payUrl })}>{payUrl}</Text>
        </View>
      )}

      <View className="submit-btn" onClick={doPay}>
        <Text>{channel === 'BALANCE' ? '余额支付' : `使用${channel === 'WECHAT' ? '微信' : '支付宝'}`}</Text>
      </View>
    </View>
  );
}

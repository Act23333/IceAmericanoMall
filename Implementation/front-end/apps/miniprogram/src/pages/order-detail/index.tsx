import { View, Text } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

const STATUS_MAP: Record<number, string> = { 1: '待付款', 2: '待发货', 3: '待收货', 4: '已完成', 5: '已取消' };

export default function OrderDetailPage() {
  const [order, setOrder] = useState<any>(null);

  useLoad((options) => {
    if (options?.orderNo) fetchOrder(options.orderNo);
  });

  async function fetchOrder(orderNo: string) {
    try { const data = await apiClient<any>(`/api/trade/order/${orderNo}`); setOrder(data); } catch {}
  }

  async function cancel() {
    if (!order) return;
    try {
      await apiClient(`/api/trade/order/${order.orderNo}/cancel`, { method: 'POST' });
      Taro.showToast({ title: '已取消', icon: 'success' });
      fetchOrder(order.orderNo);
    } catch {}
  }

  async function confirm() {
    if (!order) return;
    try {
      await apiClient(`/api/trade/order/${order.orderNo}/confirm`, { method: 'POST' });
      Taro.showToast({ title: '已确认收货', icon: 'success' });
      fetchOrder(order.orderNo);
    } catch {}
  }

  if (!order) return <View className="page"><Text>加载中...</Text></View>;

  return (
    <View className="page">
      <Text className="page-title">订单详情</Text>
      <View className="order-status-bar">
        <Text className="status-text">{STATUS_MAP[order.status] || order.status}</Text>
        <Text className="order-no-text">{order.orderNo}</Text>
      </View>
      <View className="section">
        <Text className="section-label">收货信息</Text>
        <Text className="info-text">{order.receiverName} {order.receiverPhone}</Text>
        <Text className="info-text">{order.receiverAddress}</Text>
      </View>
      <View className="section">
        <Text className="section-label">商品清单</Text>
        {order.items?.map((item: any) => (
          <View key={item.id} className="order-item">
            <Text className="item-name">{item.productName} x{item.quantity}</Text>
            <Text className="item-price">¥{formatPrice(item.price * item.quantity)}</Text>
          </View>
        ))}
      </View>
      <View className="order-summary">
        <Text>商品总额 ¥{formatPrice(order.totalAmount)}</Text>
        {order.discountAmount > 0 && <Text>优惠 ¥{formatPrice(order.discountAmount)}</Text>}
        <Text className="pay-amount">实付 ¥{formatPrice(order.payAmount)}</Text>
      </View>
      <View className="order-actions">
        {order.status === 1 && <View className="action-btn" onClick={cancel}><Text>取消订单</Text></View>}
        {order.status === 1 && <View className="action-btn primary" onClick={() => Taro.navigateTo({ url: `/pages/pay/index?orderNo=${order.orderNo}` })}><Text>去支付</Text></View>}
        {order.status === 3 && <View className="action-btn primary" onClick={confirm}><Text>确认收货</Text></View>}
      </View>
    </View>
  );
}

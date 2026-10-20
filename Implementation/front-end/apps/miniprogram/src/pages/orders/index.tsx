import { View, Text } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

const STATUS_MAP: Record<number, string> = { 1: '待付款', 2: '待发货', 3: '待收货', 4: '已完成', 5: '已取消' };
const TABS = [{ label: '全部', value: null }, { label: '待付款', value: 1 }, { label: '待发货', value: 2 }, { label: '待收货', value: 3 }];

export default function OrdersPage() {
  const [orders, setOrders] = useState<any[]>([]);
  const [status, setStatus] = useState<number | null>(null);

  useDidShow(() => fetchOrders());

  async function fetchOrders() {
    try {
      const q = new URLSearchParams({ page: '1', size: '20' });
      if (status) q.set('status', String(status));
      const data = await apiClient<any>(`/api/trade/order/page?${q.toString()}`);
      setOrders(data?.records || []);
    } catch {}
  }

  async function cancel(orderNo: string) {
    try {
      await apiClient(`/api/trade/order/${orderNo}/cancel`, { method: 'POST' });
      Taro.showToast({ title: '已取消', icon: 'success' });
      fetchOrders();
    } catch {}
  }

  async function confirm(orderNo: string) {
    try {
      await apiClient(`/api/trade/order/${orderNo}/confirm`, { method: 'POST' });
      Taro.showToast({ title: '已确认收货', icon: 'success' });
      fetchOrders();
    } catch {}
  }

  return (
    <View className="page">
      <Text className="page-title">我的订单</Text>
      <View className="tab-row">
        {TABS.map((t) => (
          <Text key={t.label} className={`tab ${status === t.value ? 'active' : ''}`}
            onClick={() => { setStatus(t.value as any); setTimeout(fetchOrders, 0); }}>
            {t.label}
          </Text>
        ))}
      </View>

      {orders.length === 0 ? (
        <View className="empty"><Text>暂无订单</Text></View>
      ) : (
        orders.map((o: any) => (
          // ★ 字段更正：OrderVO 为 snake_case（order_no / pay_amount），OrderItemVO 为 product_name
          <View key={o.order_no} className="order-card" onClick={() => Taro.navigateTo({ url: `/pages/order-detail/index?orderNo=${o.order_no}` })}>
            <View className="order-header">
              <Text className="order-no">{o.order_no}</Text>
              <Text className="order-status">{STATUS_MAP[o.status] || o.status}</Text>
            </View>
            {o.items?.slice(0, 3).map((item: any) => (
              <View key={item.id} className="order-item">
                <Text className="item-name">{item.product_name} x{item.quantity}</Text>
                <Text className="item-price">¥{formatPrice(item.price)}</Text>
              </View>
            ))}
            <View className="order-footer">
              <Text className="order-total">合计 ¥{formatPrice(o.pay_amount)}</Text>
              <View className="order-actions" onClick={(e: any) => e.stopPropagation()}>
                {o.status === 1 && <View className="action-btn" onClick={() => cancel(o.order_no)}><Text>取消</Text></View>}
                {o.status === 1 && <View className="action-btn primary" onClick={() => Taro.navigateTo({ url: `/pages/pay/index?orderNo=${o.order_no}` })}><Text>去支付</Text></View>}
                {o.status === 3 && <View className="action-btn primary" onClick={() => confirm(o.order_no)}><Text>确认收货</Text></View>}
              </View>
            </View>
          </View>
        ))
      )}
    </View>
  );
}

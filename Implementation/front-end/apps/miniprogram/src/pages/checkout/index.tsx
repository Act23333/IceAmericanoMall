import { View, Text } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

export default function CheckoutPage() {
  const [addresses, setAddresses] = useState<any[]>([]);
  const [addrId, setAddrId] = useState<number | null>(null);
  const [cartItems, setCartItems] = useState<any[]>([]);
  const [remk, setRemk] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useLoad(async () => {
    try {
      const [addrData, cartData] = await Promise.all([
        apiClient<any[]>('/api/user/address/list'),
        apiClient<any>('/api/cart'),
      ]);
      setAddresses(addrData || []);
      const selected = (cartData?.items || []).filter((i: any) => i.selected);
      setCartItems(selected);
      if (addrData?.length > 0) setAddrId(addrData.find((a: any) => a.defaulted)?.id || addrData[0].id);
    } catch {}
  });

  async function submit() {
    if (!addrId) { Taro.showToast({ title: '请选择地址', icon: 'none' }); return; }
    setSubmitting(true);
    try {
      const order = await apiClient<any>('/api/trade/order', {
        method: 'POST',
        body: JSON.stringify({ addressId: addrId, cartItemIds: cartItems.map((i: any) => i.skuId), remark: remk }),
      });
      Taro.redirectTo({ url: `/pages/pay/index?orderNo=${order.orderNo}` });
    } catch (e: any) {
      Taro.showToast({ title: e?.message || '下单失败', icon: 'none' });
    } finally { setSubmitting(false); }
  }

  const total = cartItems.reduce((s: number, i: any) => s + i.price * i.quantity, 0);

  return (
    <View className="page">
      <Text className="page-title">确认订单</Text>

      {/* Address */}
      <View className="section-title">收货地址</View>
      {addresses.map((a: any) => (
        <View key={a.id} className={`addr-card ${addrId === a.id ? 'selected' : ''}`} onClick={() => setAddrId(a.id)}>
          <Text className="addr-name">{a.receiver} {a.phone}</Text>
          <Text className="addr-detail">{a.province}{a.city}{a.district}{a.street}{a.detail}</Text>
          {a.defaulted && <Text className="addr-tag">默认</Text>}
        </View>
      ))}

      {/* Items */}
      <View className="section-title">商品清单</View>
      {cartItems.map((item: any, i: number) => (
        <View key={i} className="order-item">
          <Text className="item-name">{item.productName} x{item.quantity}</Text>
          <Text className="item-price">¥{formatPrice(item.price * item.quantity)}</Text>
        </View>
      ))}

      {/* Total */}
      <View className="total-row">
        <Text>实付金额</Text>
        <Text className="total-price">¥{formatPrice(total)}</Text>
      </View>

      <View className="submit-btn" onClick={submit}><Text>{submitting ? '提交中...' : '提交订单'}</Text></View>
    </View>
  );
}

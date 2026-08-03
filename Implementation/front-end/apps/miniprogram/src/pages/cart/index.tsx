import { View, Text, Image } from '@tarojs/components';
import Taro, { useDidShow } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

export default function CartPage() {
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useDidShow(() => fetchCart());

  async function fetchCart() {
    setLoading(true);
    try {
      const data = await apiClient<any>('/api/cart');
      setItems(data?.items || []);
    } catch {} finally { setLoading(false); }
  }

  async function updateQty(skuId: number, qty: number) {
    if (qty <= 0) { await removeItem(skuId); return; }
    try { await apiClient('/api/cart/item', { method: 'PUT', body: JSON.stringify({ skuId, quantity: qty }) }); fetchCart(); }
    catch {}
  }

  async function removeItem(skuId: number) {
    try { await apiClient(`/api/cart/item/${skuId}`, { method: 'DELETE' }); fetchCart(); }
    catch {}
  }

  async function toggleSelect(skuId: number, sel: boolean) {
    try { await apiClient(`/api/cart/item/${skuId}/selected?selected=${sel}`, { method: 'PATCH' }); fetchCart(); }
    catch {}
  }

  const selectedItems = items.filter((i: any) => i.selected);
  const totalCents = selectedItems.reduce((s: number, i: any) => s + i.price * i.quantity, 0);

  if (loading) return <View className="page"><Text className="loading-text">加载中...</Text></View>;

  if (items.length === 0) {
    return (
      <View className="page empty-page">
        <Text className="empty-icon">🛒</Text>
        <Text className="empty-text">购物车是空的</Text>
        <View className="empty-btn" onClick={() => Taro.switchTab({ url: '/pages/marketplace/index' })}>
          <Text>去逛商城</Text>
        </View>
      </View>
    );
  }

  return (
    <View className="page">
      <View className="cart-list">
        {items.map((item: any) => (
          <View key={item.skuId} className="cart-item">
            <View className={`check-box ${item.selected ? 'checked' : ''}`} onClick={() => toggleSelect(item.skuId, !item.selected)}>
              {item.selected && <Text>✓</Text>}
            </View>
            {item.image ? (
              <Image src={item.image} mode="aspectFill" style="width:120rpx;height:120rpx;border-radius:12rpx" />
            ) : (
              <View style="width:120rpx;height:120rpx;border-radius:12rpx;background:#F5F3F0;display:flex;align-items:center;justify-content:center">
                <Text style="font-size:40rpx;opacity:0.5">🛍</Text>
              </View>
            )}
            <View className="cart-info">
              <Text className="cart-name">{item.productName}</Text>
              {item.spec && <Text className="cart-spec">{item.spec}</Text>}
              <Text className="cart-price">¥{formatPrice(item.price)}</Text>
            </View>
            <View className="cart-qty">
              <View className="qty-btn" onClick={() => updateQty(item.skuId, item.quantity - 1)}><Text>-</Text></View>
              <Text className="qty-val">{item.quantity}</Text>
              <View className="qty-btn" onClick={() => updateQty(item.skuId, item.quantity + 1)}><Text>+</Text></View>
            </View>
          </View>
        ))}
      </View>

      {selectedItems.length > 0 && (
        <View className="cart-bottom">
          <View className="cart-summary">
            <Text className="total-label">合计: </Text>
            <Text className="total-price">¥{formatPrice(totalCents)}</Text>
          </View>
          <View className="checkout-btn" onClick={() => Taro.navigateTo({ url: '/pages/checkout/index' })}>
            <Text>去结算 ({selectedItems.length})</Text>
          </View>
        </View>
      )}
    </View>
  );
}

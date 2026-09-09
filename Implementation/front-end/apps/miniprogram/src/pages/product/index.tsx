import { View, Text, Image } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import { formatPrice } from '../../utils/format';

export default function ProductPage() {
  const [product, setProduct] = useState<any>(null);
  const [selectedSku, setSelectedSku] = useState<any>(null);
  const [qty, setQty] = useState(1);

  useLoad((options) => {
    if (options?.id) fetchProduct(Number(options.id));
  });

  async function fetchProduct(id: number) {
    try {
      const data = await apiClient<any>(`/api/item/product/${id}`);
      setProduct(data);
      if (data?.skus?.length > 0) setSelectedSku(data.skus[0]);
      // 记录浏览历史（fire-and-forget）
      apiClient(`/api/user/history?productId=${id}`, { method: 'POST' }).catch(() => {});
    } catch { Taro.showToast({ title: '商品不存在', icon: 'none' }); }
  }

  async function addToCart() {
    if (!selectedSku) return;
    try {
      await apiClient('/api/cart/item', { method: 'POST', body: JSON.stringify({ skuId: selectedSku.id, quantity: qty }) });
      Taro.showToast({ title: '已加入购物车', icon: 'success' });
    } catch (e: any) {
      Taro.showToast({ title: e?.message || '加购失败', icon: 'none' });
    }
  }

  async function buyNow() {
    if (!selectedSku) return;
    try {
      Taro.navigateTo({ url: `/pages/checkout/index?skuId=${selectedSku.id}&quantity=${qty}` });
    } catch {}
  }

  if (!product) return <View className="page"><Text>加载中...</Text></View>;

  return (
    <View className="page">
      {/* 商品图片 — 有真实URL用Image，否则用View占位 */}
      {product.mainImage ? (
        <Image src={product.mainImage} mode="aspectFill" style="width:100%;height:600rpx" />
      ) : (
        <View style="width:100%;height:600rpx;background:#F5F3F0;display:flex;align-items:center;justify-content:center;flex-direction:column">
          <Text style="font-size:120rpx;opacity:0.4">📷</Text>
          <Text style="font-size:28rpx;color:#9B9B9B;margin-top:16rpx">{product.name}</Text>
        </View>
      )}

      <View className="detail-body">
        {/* Info */}
        <Text className="detail-name">{product.name}</Text>
        {product.description && <Text className="detail-desc">{product.description}</Text>}

        {/* Price */}
        <View className="detail-price-row">
          <Text className="detail-price">¥{formatPrice(selectedSku?.price || 0)}</Text>
          {selectedSku?.originalPrice && selectedSku.originalPrice > selectedSku.price && (
            <Text className="detail-original">¥{formatPrice(selectedSku.originalPrice)}</Text>
          )}
        </View>

        {/* SKU selector */}
        {product.skus?.length > 1 && (
          <View className="sku-section">
            <Text className="sku-label">规格</Text>
            <View className="sku-list">
              {product.skus.map((sku: any) => (
                <View key={sku.id} className={`sku-tag ${selectedSku?.id === sku.id ? 'active' : ''}`}
                  onClick={() => setSelectedSku(sku)}>
                  <Text>{sku.spec}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* Quantity */}
        <View className="qty-section">
          <Text className="sku-label">数量</Text>
          <View className="qty-stepper">
            <View className="qty-btn" onClick={() => setQty(Math.max(1, qty - 1))}><Text>-</Text></View>
            <Text className="qty-val">{qty}</Text>
            <View className="qty-btn" onClick={() => setQty(qty + 1)}><Text>+</Text></View>
          </View>
        </View>
      </View>

      {/* Bottom buttons */}
      <View className="bottom-bar">
        <View className="btn-add" onClick={addToCart}><Text>加入购物车</Text></View>
        <View className="btn-buy" onClick={buyNow}><Text>立即购买</Text></View>
      </View>
    </View>
  );
}

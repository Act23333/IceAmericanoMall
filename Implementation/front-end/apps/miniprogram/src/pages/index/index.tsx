import { View, Text, ScrollView } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import { MOCK_PRODUCTS } from '../../utils/mock';
import { formatPrice } from '../../utils/format';
import ProductCard from '../../components/product-card';
import './index.scss';

const BANNER_DATA = [
  { title: '冰美精选',    sub: '东方自然主义', bg: '#E8F5F0', icon: '🍃' },
  { title: '新品首发',    sub: '手工陶瓷系列', bg: '#F5F0EB', icon: '✨' },
  { title: '限时秒杀',    sub: '每日10点开抢', bg: '#F0EBF5', icon: '⚡' },
];
const CAT_DATA = [
  { name: '居家生活', icon: '🏠', bg: '#F5F0EB' },
  { name: '服饰穿搭', icon: '👗', bg: '#F0EBF5' },
  { name: '数码好物', icon: '📱', bg: '#EBF0F5' },
  { name: '美食饮品', icon: '🍵', bg: '#F5F3EB' },
  { name: '文具书籍', icon: '📚', bg: '#EBF5F0' },
  { name: '运动户外', icon: '🏃', bg: '#F5EBEB' },
];

export default function IndexPage() {
  const [hotProducts, setHotProducts] = useState<any[]>([]);
  const [bannerIdx, setBannerIdx] = useState(0);

  useLoad(() => { loadData(); });

  async function loadData() {
    try {
      const res = await apiClient<any>('/api/item/product/page?sort=sales&order=desc&size=8');
      if (res?.records?.length > 0) setHotProducts(res.records);
    } catch {}
  }

  // Banner 自动轮播
  Taro.useDidShow(() => {
    const t = setInterval(() => setBannerIdx(i => (i + 1) % BANNER_DATA.length), 3500);
    return () => clearInterval(t);
  });

  const b = BANNER_DATA[bannerIdx];

  return (
    <View className="page">
      {/* Banner — View 背景色，零网络依赖 */}
      <View style={`width:100%;height:300rpx;background:${b.bg};border-radius:24rpx;display:flex;flex-direction:column;align-items:center;justify-content:center;margin-bottom:24rpx`}
        onClick={() => Taro.switchTab({ url: '/pages/marketplace/index' })}>
        <Text style="font-size:72rpx">{b.icon}</Text>
        <Text style="font-size:34rpx;font-weight:700;color:#1A1A1A;margin-top:8rpx">{b.title}</Text>
        <Text style="font-size:24rpx;color:#9B9B9B;margin-top:4rpx">{b.sub}</Text>
        {/* 轮播指示器 */}
        <View style="display:flex;gap:12rpx;margin-top:16rpx">
          {BANNER_DATA.map((_, i) => (
            <View key={i} onClick={(e: any) => { e.stopPropagation(); setBannerIdx(i); }}
              style={`width:${i === bannerIdx ? 24 : 8}rpx;height:8rpx;border-radius:4rpx;background:${i === bannerIdx ? '#2D8B6E' : '#C0C0C0'};transition:all 0.3s`} />
          ))}
        </View>
      </View>

      {/* Hot products — 热卖推荐，放在分类上面 */}
      <View className="section">
        <View className="section-header">
          <Text className="section-title">🔥 大家都在买</Text>
          <Text className="section-more" onClick={() => Taro.switchTab({ url: '/pages/marketplace/index' })}>查看全部 →</Text>
        </View>
        <View className="product-grid">
          {(hotProducts.length > 0 ? hotProducts : MOCK_PRODUCTS).map((p: any) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </View>
      </View>

      {/* Categories */}
      <View className="section">
        <Text className="section-title">探索分类</Text>
        <View className="category-grid">
          {CAT_DATA.map((cat, i) => (
            <View key={i} className="category-item" onClick={() => {
              Taro.setStorageSync('activeCategoryId', i + 1);
              Taro.switchTab({ url: '/pages/marketplace/index' });
            }}>
              <View style={`width:80rpx;height:80rpx;background:${cat.bg};border-radius:20rpx;display:flex;align-items:center;justify-content:center;margin:0 auto 8rpx`}>
                <Text style="font-size:36rpx">{cat.icon}</Text>
              </View>
              <Text className="category-name">{cat.name}</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

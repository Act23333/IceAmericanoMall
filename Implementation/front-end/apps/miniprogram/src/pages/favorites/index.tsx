import { View, Text } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { useDidShow } from '@tarojs/taro';
import { apiClient } from '../../utils/api';
import ProductCard from '../../components/product-card';

export default function FavoritesPage() {
  const [list, setList] = useState<any[]>([]);

  useDidShow(async () => {
    try { const data = await apiClient<any>('/api/user/favorite?page=1&size=50'); setList(data?.records || []); } catch {}
  });

  return (
    <View className="page">
      <Text className="page-title">我的收藏</Text>
      {list.length === 0 ? <View className="empty"><Text>还没有收藏商品</Text></View> :
        <View className="product-grid">{list.map((p: any) => <ProductCard key={p.id} product={p} />)}</View>}
    </View>
  );
}

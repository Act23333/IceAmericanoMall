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
        <View className="product-grid">
          {/* ★ 字段更正 + 边界映射：后端 FavoriteVO 只有 { id, user_id, product_id, create_time }，
              不投影商品名/主图/价（契约 §4.2.6 示例与实现不符）。
              故此处把 `product_id` 映射为卡片的 `id`（卡片用它做跳转目标）。 */}
          {list.map((f: any) => (
            <ProductCard key={f.id} product={{ id: f.product_id, name: `商品 #${f.product_id}` }} />
          ))}
        </View>}
    </View>
  );
}

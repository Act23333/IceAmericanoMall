import { View, Text } from '@tarojs/components';
import './index.scss';

export default function CartPage() {
  return (
    <View className="page">
      <View className="empty-cart">
        <Text className="empty-icon">🛒</Text>
        <Text className="empty-title">购物车是空的</Text>
        <Text className="empty-desc">去商城逛逛，发现心仪好物</Text>
        <View className="empty-btn">去逛商城</View>
      </View>
    </View>
  );
}

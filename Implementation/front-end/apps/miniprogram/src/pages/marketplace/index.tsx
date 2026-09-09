import { View, Text } from '@tarojs/components';
import './index.scss';

export default function MarketplacePage() {
  return (
    <View className="page">
      <View className="header">
        <Text className="header-title">商城</Text>
        <Text className="header-desc">精选好物，品质之选</Text>
      </View>

      <View className="section">
        <Text className="section-title">分类</Text>
        <View className="category-grid">
          {['居家生活', '服饰穿搭', '数码好物', '美食饮品', '文具书籍', '运动户外'].map((name) => (
            <View key={name} className="category-item">
              <View className="category-icon" />
              <Text className="category-name">{name}</Text>
            </View>
          ))}
        </View>
      </View>

      <View className="section">
        <Text className="section-title">热销推荐</Text>
        <View className="product-list">
          {['手工陶瓷茶杯', '亚麻编织毯', '原木台灯', '铜质书签'].map((name) => (
            <View key={name} className="product-card">
              <View className="product-image" />
              <Text className="product-name">{name}</Text>
              <Text className="product-price">¥128.00</Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

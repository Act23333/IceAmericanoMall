/**
 * 小程序首页 — 品牌主页
 */
import { View, Text, Image } from '@tarojs/components';
import { useLoad } from '@tarojs/taro';
import './index.scss';

export default function IndexPage() {
  useLoad(() => {
    console.log('[首页] 页面加载');
  });

  return (
    <View className="page">
      {/* 品牌 Hero */}
      <View className="hero">
        <Text className="hero-title">冰美商城</Text>
        <Text className="hero-tagline">Technology meets everyday life</Text>
        <Text className="hero-desc">科技自然融入生活</Text>
      </View>

      {/* 分类入口 */}
      <View className="section">
        <Text className="section-title">探索分类</Text>
        <View className="category-grid">
          {['居家生活', '服饰穿搭', '数码好物', '美食饮品', '文具书籍', '运动户外'].map((name) => (
            <View key={name} className="category-item">
              <View className="category-icon" />
              <Text className="category-name">{name}</Text>
            </View>
          ))}
        </View>
      </View>

      {/* 推荐商品 (占位) */}
      <View className="section">
        <Text className="section-title">精选推荐</Text>
        <View className="product-placeholder">
          <Text className="placeholder-text">商品数据加载中…</Text>
          <Text className="placeholder-hint">请先启动后端服务</Text>
        </View>
      </View>
    </View>
  );
}

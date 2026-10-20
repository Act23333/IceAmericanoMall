import { View, Text, Image } from '@tarojs/components';
import { formatPrice } from '../utils/format';
import Taro from '@tarojs/taro';

const COLORS = ['#E8F5F0','#F5F0EB','#EDF0F5','#F5EDEB','#EBF3F5','#F3EBF5','#F5F3EB','#EBF5F3'];

/**
 * 卡片可消费的最小报文形状（字段名 = 线上 JSON 键名，snake_case）。
 * 兼容 `ProductVO`（列表页只有 min_price / 详情页有 skus）与 `ProductSearchVO`（price）。
 */
interface Props {
  product: {
    id: number;
    name: string;
    main_image?: string | null;
    /** ProductVO 列表页展示价（分） */
    min_price?: number | null;
    /** ProductSearchVO 最低 SKU 价（分） */
    price?: number | null;
    sold_count?: number | null;
    skus?: Array<{ price: number; image?: string }> | null;
  };
}

export default function ProductCard({ product }: Props) {
  // 逐级回落：详情页 sku → 列表页 min_price → 搜索 price
  const price = product.skus?.[0]?.price ?? product.min_price ?? product.price ?? 0;
  const bg = COLORS[product.id % COLORS.length];
  const soldCount = product.sold_count ?? 0;
  const hasRealImage = !!product.main_image;

  return (
    <View
      className="product-card"
      onClick={() => Taro.navigateTo({ url: `/pages/product/index?id=${product.id}` })}
    >
      {hasRealImage ? (
        <Image src={product.main_image ?? ''} mode="aspectFill" style="width:100%;height:340rpx" />
      ) : (
        <View style={`width:100%;height:340rpx;background:${bg};display:flex;align-items:center;justify-content:center;flex-direction:column`}>
          <Text style="font-size:80rpx;opacity:0.6">{['📦','🎁','✨','💎','🌟','🔥','🍃','🎯'][product.id % 8]}</Text>
          <Text style="font-size:24rpx;color:#9B9B9B;margin-top:12rpx">{product.name.slice(0,6)}</Text>
        </View>
      )}
      <View className="product-info">
        <Text className="product-name">{product.name}</Text>
        <View className="product-bottom">
          <Text className="product-price">¥{formatPrice(price)}</Text>
          {soldCount > 0 && <Text className="product-sold">已售 {soldCount}</Text>}
        </View>
      </View>
    </View>
  );
}

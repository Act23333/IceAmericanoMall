import { View, Text, Image } from '@tarojs/components';
import { formatPrice } from '../utils/format';
import Taro from '@tarojs/taro';
import { productImage } from '../utils/mock';

const COLORS = ['#E8F5F0','#F5F0EB','#EDF0F5','#F5EDEB','#EBF3F5','#F3EBF5','#F5F3EB','#EBF5F3'];

interface Props {
  product: {
    id: number;
    name: string;
    mainImage?: string;
    price?: number;
    soldCount?: number;
    rating?: number;
    skus?: Array<{ price: number; image?: string }>;
  };
}

export default function ProductCard({ product }: Props) {
  const price = product.skus?.[0]?.price ?? product.price ?? 0;
  const bg = COLORS[product.id % COLORS.length];
  const initial = product.name.charAt(0);
  const hasRealImage = !!product.mainImage;

  return (
    <View
      className="product-card"
      onClick={() => Taro.navigateTo({ url: `/pages/product/index?id=${product.id}` })}
    >
      {hasRealImage ? (
        <Image src={product.mainImage} mode="aspectFill" style="width:100%;height:340rpx" />
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
          {product.soldCount > 0 && <Text className="product-sold">已售 {product.soldCount}</Text>}
        </View>
      </View>
    </View>
  );
}

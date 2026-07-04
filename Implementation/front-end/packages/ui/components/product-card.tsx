import { cn } from '../lib/utils';
import { formatPrice } from '@icedmall/utils';
import type { ProductVO } from '@icedmall/api';

type ProductCardVariant = 'default' | 'glass' | 'horizontal';

interface ProductCardProps {
  product: ProductVO;
  className?: string;
  variant?: ProductCardVariant;
  onClick?: () => void;
}

const variantClasses: Record<ProductCardVariant, string> = {
  default:
    'border border-warm-200 bg-white shadow-sm hover:shadow-md',
  glass:
    'glass-card hover:shadow-glass-lg',
  horizontal:
    'border border-warm-200 bg-white shadow-sm hover:shadow-md flex flex-row',
};

/**
 * 商品卡片 — 通用组件
 *
 * 品牌风格：大图 + 干净文字 + 克制的价格展示
 * GPU-friendly hover: 仅 transform scale(1.02), 无 layout shift
 */
export function ProductCard({
  product,
  className,
  variant = 'default',
  onClick,
}: ProductCardProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => { if (e.key === 'Enter') onClick?.(); }}
      className={cn(
        'group cursor-pointer overflow-hidden rounded-2xl transition-all duration-300',
        'hover:scale-[1.02]',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-green/40',
        variantClasses[variant],
        className,
      )}
    >
      {/* 图片区域 */}
      <div
        className={cn(
          'overflow-hidden bg-warm-100',
          variant === 'horizontal' ? 'w-1/3 shrink-0' : 'aspect-square w-full',
        )}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={product.mainImage}
          alt={product.name}
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          loading="lazy"
        />
      </div>

      {/* 信息区域 */}
      <div className={cn('p-4', variant === 'horizontal' && 'flex flex-col justify-center flex-1')}>
        <h3 className="text-sm font-medium text-ink-black line-clamp-2 leading-snug">
          {product.name}
        </h3>

        {product.description && (
          <p className="mt-1 text-xs text-warm-600 line-clamp-1">
            {product.description}
          </p>
        )}

        <div className="mt-2 flex items-baseline gap-2">
          <span className="text-base font-semibold text-ink-black">
            ¥{formatPrice(product.skus?.[0]?.price ?? 0)}
          </span>
        </div>

        {/* 销量 */}
        {product.soldCount > 0 && (
          <p className="mt-1.5 text-xs text-warm-600">
            {product.soldCount > 10000
              ? `${(product.soldCount / 10000).toFixed(1)}万人已购`
              : `${product.soldCount}人已购`}
          </p>
        )}
      </div>
    </div>
  );
}

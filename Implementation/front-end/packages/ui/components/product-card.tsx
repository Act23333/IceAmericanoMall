'use client';

import Link from 'next/link';
import { cn } from '../lib/utils';
import { formatPrice } from '@icedmall/utils';

/**
 * 卡片可消费的最小报文形状 —— 兼容三种后端 VO：
 *   - `ProductVO`（列表页：只有 `min_price`，**不装配 skus**）
 *   - `ProductVO`（详情页：装配 `skus`）
 *   - `ProductSearchVO`（搜索结果：字段为 `price` = 最低 SKU 价）
 *
 * ★ 修复点：原实现只读 `skus?.[0]?.price` —— 列表页 / 搜索结果都不带 skus，
 *   导致价格恒显示 ¥0.00。现按 `skus → min_price → price` 依次回落。
 * 字段名一律 snake_case（对应线上 JSON 键名）。
 *
 * ⚠️ **`id` 与 `spu_id` 的分工（2026-10-18 B8，ADR-062 §决策 3）**：
 *   - `id` 是**资源行主键**（技术键，§1.8 例外）—— 只作渲染 `key`；
 *   - `spu_id` 是 **SPU 业务键 32-hex** —— 详情页 `/spu/{spu_id}` 的**唯一**寻址值。
 *   卡片**自己不拼链接**（`href` 由调用方经 `spuHref()` 构造），避免「拿技术键拼深链」
 *   这类静默故障在这里再长出来。
 */
export interface ProductCardData {
  id: number;
  /** SPU 业务键（32-hex）；回填缺失时为 null/undefined → 调用方不渲染链接。 */
  spu_id?: string | null;
  /**
   * SPU 名 —— 线上键 **`spu_name`**（契约 §1.8④，2026-10-18「甲b」）。
   *
   * ★ 该键在三种来源上**同一个名字**：`ProductVO`（列表/详情）、`ProductSearchVO`（搜索）、
   *   `RecommendSpuVO`（相似/搭配卡）—— 故本卡片只需读一处。历史坑：旧实现读 `name`，
   *   与旁路 VO 的 `spu_name` 并存，前端按任一键取值都会在另一侧静默 `undefined`。
   */
  spu_name: string;
  main_image?: string | null;
  description?: string | null;
  brand?: string | null;
  sold_count?: number | null;
  /** ProductVO 列表页展示价（分）。 */
  min_price?: number | null;
  /** ProductSearchVO 最低 SKU 价（分）。 */
  price?: number | null;
  skus?: Array<{ price: number }> | null;
}

type ProductCardVariant = 'default' | 'glass' | 'horizontal';

interface ProductCardProps {
  product: ProductCardData;
  className?: string;
  variant?: ProductCardVariant;
  href?: string;
  onClick?: () => void;
}

/** 展示价（分）—— 逐级回落：详情页 sku → 列表页 min_price → 搜索 price。 */
function displayPriceCents(product: ProductCardData): number {
  return product.skus?.[0]?.price ?? product.min_price ?? product.price ?? 0;
}

const variantClasses: Record<ProductCardVariant, string> = {
  default: 'border border-warm-200 bg-white shadow-sm hover:shadow-md',
  glass: 'glass-card hover:shadow-glass-lg',
  horizontal: 'border border-warm-200 bg-white shadow-sm hover:shadow-md flex flex-row',
};

const cardClasses =
  'group cursor-pointer overflow-hidden rounded-2xl transition-all duration-300 block ' +
  'border border-transparent hover:border-accent/20 hover:scale-[1.02] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40';

function CardContent({ product, variant = 'default' }: Omit<ProductCardProps, 'href' | 'onClick' | 'className'>) {
  const soldCount = product.sold_count ?? 0;
  return (
    <>
      <div className={cn('overflow-hidden bg-warm-100', variant === 'horizontal' ? 'w-1/3 shrink-0' : 'aspect-square w-full')}>
        <img src={product.main_image ?? ''} alt={product.spu_name}
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          loading="lazy" decoding="async" />
      </div>
      <div className={cn('p-4', variant === 'horizontal' && 'flex flex-col justify-center flex-1')}>
        <h3 className="text-sm font-medium text-ink-black line-clamp-2 leading-snug">{product.spu_name}</h3>
        {product.description && <p className="mt-1 text-xs text-warm-600 line-clamp-1">{product.description}</p>}
        <div className="mt-2 flex items-baseline gap-2">
          <span className="text-base font-semibold text-ink-black">¥{formatPrice(displayPriceCents(product))}</span>
        </div>
        {soldCount > 0 && (
          <p className="mt-1.5 text-xs text-warm-600">
            {soldCount > 10000 ? `${(soldCount / 10000).toFixed(1)}万人已购` : `${soldCount}人已购`}
          </p>
        )}
      </div>
    </>
  );
}

/** 商品卡片 — href(Server Component友好) 或 onClick(Client) */
export function ProductCard({ product, className, variant = 'default', href, onClick }: ProductCardProps) {
  if (href) {
    return (
      <Link href={href} className={cn(cardClasses, variantClasses[variant], className)}>
        <CardContent product={product} variant={variant} />
      </Link>
    );
  }
  return (
    <div role="button" tabIndex={0} onClick={onClick}
      onKeyDown={(e) => { if (e.key === 'Enter') onClick?.(); }}
      className={cn(cardClasses, variantClasses[variant], className)}>
      <CardContent product={product} variant={variant} />
    </div>
  );
}

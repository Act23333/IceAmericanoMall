'use client';

import Link from 'next/link';
import { cn } from '../lib/utils';
import { formatPrice } from '@icedmall/utils';
import type { ProductVO } from '@icedmall/api';

type ProductCardVariant = 'default' | 'glass' | 'horizontal';

interface ProductCardProps {
  product: ProductVO;
  className?: string;
  variant?: ProductCardVariant;
  href?: string;
  onClick?: () => void;
}

const variantClasses: Record<ProductCardVariant, string> = {
  default: 'border border-warm-200 bg-white shadow-sm hover:shadow-md',
  glass: 'glass-card hover:shadow-glass-lg',
  horizontal: 'border border-warm-200 bg-white shadow-sm hover:shadow-md flex flex-row',
};

const cardClasses =
  'group cursor-pointer overflow-hidden rounded-2xl transition-all duration-300 block ' +
  'hover:scale-[1.02] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-green/40';

function CardContent({ product, variant = 'default', className }: Omit<ProductCardProps, 'href' | 'onClick'>) {
  return (
    <>
      <div className={cn('overflow-hidden bg-warm-100', variant === 'horizontal' ? 'w-1/3 shrink-0' : 'aspect-square w-full')}>
        <img src={product.mainImage} alt={product.name}
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          loading="lazy" decoding="async" />
      </div>
      <div className={cn('p-4', variant === 'horizontal' && 'flex flex-col justify-center flex-1')}>
        <h3 className="text-sm font-medium text-ink-black line-clamp-2 leading-snug">{product.name}</h3>
        {product.description && <p className="mt-1 text-xs text-warm-600 line-clamp-1">{product.description}</p>}
        <div className="mt-2 flex items-baseline gap-2">
          <span className="text-base font-semibold text-ink-black">¥{formatPrice(product.skus?.[0]?.price ?? 0)}</span>
        </div>
        {product.soldCount > 0 && (
          <p className="mt-1.5 text-xs text-warm-600">
            {product.soldCount > 10000 ? `${(product.soldCount / 10000).toFixed(1)}万人已购` : `${product.soldCount}人已购`}
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

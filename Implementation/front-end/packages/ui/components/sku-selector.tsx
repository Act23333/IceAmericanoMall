'use client';

import { cn } from '../lib/utils';

interface SkuOption {
  id: number;
  skuId: string;
  spec: string;
  price: number;
  stock: number;
  image?: string;
}

interface SkuSelectorProps {
  skus: SkuOption[];
  selectedSkuId?: string;
  onSelect: (sku: SkuOption) => void;
  className?: string;
}

/**
 * SKU 规格选择器 — Client Component
 *
 * 选中态：金色描边 + 浅绿底
 * 库存为 0：灰色 + 划线
 */
export function SkuSelector({
  skus,
  selectedSkuId,
  onSelect,
  className,
}: SkuSelectorProps) {
  if (!skus || skus.length <= 1) return null;

  return (
    <div className={cn('flex flex-wrap gap-2', className)}>
      {skus.map((sku) => {
        const isSelected = sku.skuId === selectedSkuId;
        const isOos = sku.stock <= 0;

        return (
          <button
            key={sku.skuId}
            onClick={() => !isOos && onSelect(sku)}
            disabled={isOos}
            className={cn(
              'px-4 py-2 text-sm rounded-xl border transition-all duration-150',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent-green/40',
              isSelected
                ? 'border-accent-gold bg-accent-gold/5 text-ink-black font-medium shadow-sm'
                : 'border-warm-200 bg-white text-ink-soft hover:border-warm-400',
              isOos
                ? 'opacity-40 cursor-not-allowed line-through'
                : 'cursor-pointer active:scale-[0.97]',
            )}
          >
            {sku.spec}
            {isOos && ' (缺货)'}
          </button>
        );
      })}
    </div>
  );
}

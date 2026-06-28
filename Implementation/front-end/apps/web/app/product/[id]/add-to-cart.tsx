'use client';

import { useState } from 'react';
import { Button, SkuSelector } from '@icedmall/ui';
import { useCartStore } from '@/app/stores/cart-store';
import { type ProductVO } from '@icedmall/api';

interface AddToCartProps {
  product: ProductVO;
}

/**
 * SKU 选择 + 加入购物车 — Client Component Island
 *
 * 交互：
 * 1. 用户选择 SKU → 价格联动
 * 2. 点击加购 → 写入 Zustand Cart Store
 * 3. 反馈: "✓ 已加入" 持续 1.5s
 */
export function AddToCart({ product }: AddToCartProps) {
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);

  // 默认选中第一个有库存的 SKU
  const skuOptions = (product.skus ?? []).map((s) => ({
    id: s.id,
    skuId: s.skuId,
    spec: s.spec,
    price: s.price,
    stock: s.stock,
    image: s.image,
  }));
  const defaultSku = skuOptions.find((s) => s.stock > 0);
  const [selectedSkuId, setSelectedSkuId] = useState(defaultSku?.skuId ?? '');
  const selectedSku = skuOptions.find((s) => s.skuId === selectedSkuId);

  const handleAdd = () => {
    const sku = selectedSku ?? defaultSku;
    if (!sku || sku.stock <= 0) return;

    addItem({
      skuId: sku.skuId,
      productName: product.name,
      spec: sku.spec,
      image: sku.image || product.mainImage || '',
      price: sku.price,
      quantity: 1,
    });

    setAdded(true);
    setTimeout(() => setAdded(false), 1500);
  };

  return (
    <div className="mt-8 space-y-4">
      {/* SKU 选择器 */}
      {skuOptions.length > 1 && (
        <div>
          <p className="text-sm text-ink-soft font-medium mb-3">规格</p>
          <SkuSelector
            skus={skuOptions}
            selectedSkuId={selectedSkuId}
            onSelect={(sku) => setSelectedSkuId(sku.skuId)}
          />
        </div>
      )}

      {/* 选中 SKU 价格 */}
      {selectedSku && selectedSku.price !== (product.skus?.[0]?.price) && (
        <p className="text-sm text-warm-600">
          已选: <span className="text-ink-black font-medium">¥{(selectedSku.price / 100).toFixed(2)}</span>
        </p>
      )}

      {/* 加购按钮 */}
      <div className="flex items-center gap-3 pt-2">
        <Button
          onClick={handleAdd}
          disabled={!selectedSku || selectedSku.stock <= 0}
          variant="primary"
          size="lg"
          className="min-w-[200px]"
          loading={false}
        >
          {added
            ? '✓ 已加入购物车'
            : (!selectedSku || selectedSku.stock <= 0)
              ? '暂时缺货'
              : '加入购物车'}
        </Button>
      </div>
    </div>
  );
}

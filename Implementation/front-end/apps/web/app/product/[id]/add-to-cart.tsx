'use client';

import { useState, useEffect } from 'react';
import { Button, SkuSelector, PriceDisplay, QuantityStepper } from '@icedmall/ui';
import { useCartStore } from '@/app/stores/cart-store';
import { type ProductVO } from '@icedmall/api';

interface AddToCartProps {
  product: ProductVO;
}

/**
 * SKU 选择 + 数量步进 + 加入购物车 — Client Component
 *
 * 京东风格: 价格联动 | 数量± | 加购反馈
 */
export function AddToCart({ product }: AddToCartProps) {
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);
  const [quantity, setQuantity] = useState(1);

  // SKU 选项
  const skuOptions = (product.skus ?? []).map((s) => ({
    id: s.id, skuId: s.skuId, spec: s.spec, price: s.price, stock: s.stock, image: s.image,
  }));
  const defaultSku = skuOptions.find((s) => s.stock > 0);
  const [selectedSkuId, setSelectedSkuId] = useState(defaultSku?.skuId ?? '');
  const selectedSku = skuOptions.find((s) => s.skuId === selectedSkuId);

  // Issue 10: 切换 SKU 时重置"已加入购物车"状态
  useEffect(() => { setAdded(false); }, [selectedSkuId]);

  // Issue 11: 数量步进
  const stock = selectedSku?.stock ?? 0;

  const handleAdd = () => {
    const sku = selectedSku ?? defaultSku;
    if (!sku || sku.stock <= 0) return;
    addItem({
      skuId: sku.skuId,
      productName: product.name,
      spec: sku.spec,
      image: sku.image || product.mainImage || '',
      price: sku.price,
      quantity,
    });
    setAdded(true);
    // 重置数量
    setQuantity(1);
    setTimeout(() => setAdded(false), 1500);
  };

  return (
    <div className="mt-8 space-y-5">
      {/* Issue 8: 主价格联动 — 放在 Client 组件内读取 selectedSku */}
      <div>
        <PriceDisplay
          cents={selectedSku?.price ?? defaultSku?.price ?? 0}
          size="lg"
        />
        {selectedSku && selectedSku.price !== (defaultSku?.price) && (
          <span className="text-xs text-warm-400 line-through ml-2">
            ¥{(defaultSku?.price ?? 0) / 100}
          </span>
        )}
      </div>

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

      {/* 数量选择器 */}
      <div>
        <p className="text-sm text-ink-soft font-medium mb-3">数量</p>
        <QuantityStepper
          value={quantity}
          onChange={setQuantity}
          max={Math.min(stock, 999)}
        />
        {stock > 0 && stock < 10 && (
          <span className="ml-3 text-xs text-danger">仅剩 {stock} 件</span>
        )}
      </div>

      {/* 加购按钮 */}
      <div className="flex items-center gap-3 pt-2">
        <Button
          onClick={handleAdd}
          disabled={!selectedSku || stock <= 0}
          variant="primary"
          size="lg"
          className="min-w-[200px]"
        >
          {added
            ? '✓ 已加入购物车'
            : stock <= 0
              ? '暂时缺货'
              : '加入购物车'}
        </Button>
      </div>
    </div>
  );
}

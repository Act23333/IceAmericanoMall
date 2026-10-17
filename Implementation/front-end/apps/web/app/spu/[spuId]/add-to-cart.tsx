'use client';

import { useState, useEffect } from 'react';
import { Button, SkuSelector, PriceDisplay, QuantityStepper } from '@icedmall/ui';
import { useCartModel } from '@/app/stores/cart-model';
import { type ProductVO } from '@icedmall/api';

interface AddToCartProps {
  product: ProductVO;
}

/**
 * SKU 选择 + 数量步进 + 加入购物车 — Client Component
 *
 * 京东风格: 价格联动 | 数量± | 加购反馈
 *
 * ── 加购接线（2026-10-17 B3）────────────────────────────────────
 * 旧实现只写本地 Zustand，而**下单恒取服务端购物车**（`CreateOrderReq` 不承载购物车项 ID）
 * ⇒ 主链路断裂。现改为经 `useCartModel().add()`：
 *   - 未登录 → 只写本地（行为与改造前等价，未登录可用性不变）；
 *   - 已登录 → 乐观写缓存（**即时反馈不等待网络**）+ `POST /api/cart/item`，失败回滚并提示。
 */
export function AddToCart({ product }: AddToCartProps) {
  const { add } = useCartModel();
  const [added, setAdded] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);

  // 记录浏览历史 (fire-and-forget, 带JWT认证)
  //
  // ⚠️ 两处修正（B3）：
  //   1. query key 为 **`spu_id`**（后端 `@RequestParam(name = "spu_id") String spuId`），
  //      旧代码发 `product_id` → 该参数为**必填**，缺失即 **400**（不是静默忽略）⇒ 足迹记录 100% 失败；
  //   2. 取值须为 **SPU 业务键 32-hex**（`product.spu_id`），不能再传技术键 `product.id`
  //      （契约 §1.8② / ADR-061）。
  useEffect(() => {
    if (!product.spu_id) return; // 业务键缺失（降级）→ 不发必然失败的请求
    const base = process.env.NEXT_PUBLIC_API_URL || '';
    const token = document.cookie.split('; ').find(r => r.startsWith('access_token='))?.split('=')[1];
    fetch(`${base}/api/user/history?spu_id=${product.spu_id}`, {
      method: 'POST',
      credentials: 'include',
      headers: token ? { Authorization: `Bearer ${decodeURIComponent(token)}` } : {},
    }).then(r => {
      if (!r.ok) console.warn('[足迹] 记录失败:', r.status);
    }).catch(e => {
      console.warn('[足迹] 请求异常:', e.message);
    });
  }, [product.spu_id]);

  // SKU 选项 —— ⚠️ 这里是字段边界转换点之一：
  // 后端报文 `SkuVO`（snake_case）→ 前端本地视图模型（camelCase）。
  // 理由：本地购物车是前端自有域模型（Zustand + localStorage），不属于后端报文，
  // 故标识符保持 camelCase；`sku_id`（SKU 业务键 32-hex）**原样透传**，不做类型转换。
  const skuOptions = (product.skus ?? []).map((s) => ({
    id: s.id, skuId: s.sku_id, spec: s.spec, price: s.price, stock: s.stock, image: s.image,
  }));
  const defaultSku = skuOptions.find((s) => s.stock > 0);
  const [selectedSkuId, setSelectedSkuId] = useState(defaultSku?.skuId ?? '');
  const selectedSku = skuOptions.find((s) => s.skuId === selectedSkuId);

  // Issue 10: 切换 SKU 时重置"已加入购物车"状态
  useEffect(() => { setAdded(false); setAddError(null); }, [selectedSkuId]);

  // Issue 11: 数量步进
  const stock = selectedSku?.stock ?? 0;

  const handleAdd = async () => {
    const sku = selectedSku ?? defaultSku;
    if (!sku || sku.stock <= 0) return;
    setAddError(null);
    setAdded(true);           // 即时反馈（先亮，不等网络往返）
    setQuantity(1);
    try {
      await add({
        skuId: sku.skuId,
        spuId: product.spu_id ?? null,
        spuName: product.name,
        spec: sku.spec,
        image: sku.image || product.main_image || '',
        price: sku.price,
        quantity,
      });
      setTimeout(() => setAdded(false), 1500);
    } catch (e: unknown) {
      // 失败态：本仓全局吞错误的反模式不在此复制 —— 明确回退按钮态并提示
      setAdded(false);
      setAddError(e instanceof Error ? e.message : '加入购物车失败');
    }
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

      {/* 失败态（不吞错误） */}
      {addError && (
        <p role="alert" className="text-xs text-danger">
          加入购物车失败：{addError}
        </p>
      )}
    </div>
  );
}

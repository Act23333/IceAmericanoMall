'use client';

import { useCartStore } from '@/app/stores/cart-store';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import { Trash2, Minus, Plus, ShoppingBag } from 'lucide-react';
import Link from 'next/link';

/**
 * 购物车页 — Client Component
 *
 * 数据源: Zustand Cart Store (localStorage 持久化)
 * Phase 5: 登录后同步到服务端购物车 API
 */
export default function CartPage() {
  const { items, removeItem, updateQuantity, toggleSelect, selectAll, deselectAll, clearCart } =
    useCartStore();

  const selectedItems = items.filter((i) => i.selected);
  const allSelected = items.length > 0 && items.every((i) => i.selected);
  const totalCents = selectedItems.reduce((sum, i) => sum + i.price * i.quantity, 0);

  return (
    <div className="mx-auto max-w-3xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-light text-ink-black">购物车</h1>
        {items.length > 0 && (
          <button
            onClick={clearCart}
            className="text-sm text-warm-400 hover:text-danger transition-colors"
          >
            清空
          </button>
        )}
      </div>

      {/* ── 空购物车 ── */}
      {items.length === 0 && (
        <GlassCard className="p-16 text-center">
          <ShoppingBag className="mx-auto h-12 w-12 text-warm-300" />
          <h2 className="mt-4 text-lg font-medium text-ink-black">购物车是空的</h2>
          <p className="mt-2 text-sm text-warm-600">去商城逛逛，发现心仪好物</p>
          <Link href="/marketplace" className="mt-6 inline-block">
            <Button variant="primary" size="md">去逛商城</Button>
          </Link>
        </GlassCard>
      )}

      {/* ── 全选 ── */}
      {items.length > 0 && (
        <div className="mb-4 flex items-center gap-3">
          <button
            onClick={allSelected ? deselectAll : selectAll}
            className="text-sm text-warm-600 hover:text-ink-black transition-colors"
          >
            {allSelected ? '取消全选' : '全选'}
          </button>
        </div>
      )}

      {/* ── 购物车列表 ── */}
      {items.length > 0 && (
        <div className="space-y-3">
          {items.map((item) => (
            <GlassCard key={item.skuId} className="flex gap-4 p-4" blur="sm">
              {/* 选中框 */}
              <button
                onClick={() => toggleSelect(item.skuId)}
                className={`mt-4 h-5 w-5 shrink-0 rounded-full border-2 flex items-center justify-center transition-colors ${
                  item.selected
                    ? 'border-accent-green bg-accent-green text-white'
                    : 'border-warm-300'
                }`}
              >
                {item.selected && <span className="text-xs">✓</span>}
              </button>

              {/* 图片 */}
              <div className="h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-warm-100">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={item.image}
                  alt={item.productName}
                  className="h-full w-full object-cover"
                  loading="lazy"
                />
              </div>

              {/* 信息 */}
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-medium text-ink-black truncate">
                  {item.productName}
                </h3>
                {item.spec && (
                  <p className="text-xs text-warm-400 mt-0.5">{item.spec}</p>
                )}
                <PriceDisplay cents={item.price} size="sm" className="mt-2" />
              </div>

              {/* 数量 + 删除 */}
              <div className="flex flex-col items-end gap-2">
                <button
                  onClick={() => removeItem(item.skuId)}
                  className="p-1 text-warm-400 hover:text-danger transition-colors"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
                <div className="flex items-center gap-1 border border-warm-200 rounded-lg">
                  <button
                    onClick={() => updateQuantity(item.skuId, item.quantity - 1)}
                    className="px-2 py-1 text-warm-600 hover:text-ink-black transition-colors"
                  >
                    <Minus className="h-3 w-3" />
                  </button>
                  <span className="text-sm text-ink-black min-w-[20px] text-center">
                    {item.quantity}
                  </span>
                  <button
                    onClick={() => updateQuantity(item.skuId, item.quantity + 1)}
                    className="px-2 py-1 text-warm-600 hover:text-ink-black transition-colors"
                  >
                    <Plus className="h-3 w-3" />
                  </button>
                </div>
              </div>
            </GlassCard>
          ))}
        </div>
      )}

      {/* ── 底部结算栏 ── */}
      {selectedItems.length > 0 && (
        <div className="mt-8 sticky bottom-4">
          <GlassCard className="flex items-center justify-between p-5" blur="lg">
            <div>
              <p className="text-xs text-warm-600">
                已选 {selectedItems.length} 件，共 {selectedItems.reduce((s, i) => s + i.quantity, 0)} 件
              </p>
              <PriceDisplay cents={totalCents} size="lg" className="mt-1" />
            </div>
            <Link href="/shop/checkout">
              <Button variant="gold" size="lg">
                去结算
              </Button>
            </Link>
          </GlassCard>
        </div>
      )}
    </div>
  );
}

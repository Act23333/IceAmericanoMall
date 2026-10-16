'use client';

import { useCartModel } from '@/app/stores/cart-model';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { Trash2, Minus, Plus, ShoppingBag } from 'lucide-react';
import Link from 'next/link';
import { useState } from 'react';

/**
 * 购物车页 — Client Component
 *
 * ── 数据源（2026-10-17 B3 改造）──────────────────────────────────
 * **服务端购物车为准，客户端只是视图**（`useCartModel()`）：
 *   - 未登录 → 本地 Zustand（localStorage），行为与改造前等价；
 *   - 已登录 → `GET /api/cart`；改量/勾选/删除/清空走 `/api/cart/**`（乐观 + 失败回滚）；
 *   - 已登录但服务端不可达 → 降级读本地并**显式提示离线态**（不静默伪装成功）。
 * 下单只认服务端购物车已勾选项，故本页勾选状态必须落到服务端，否则结算必然「购物车为空」。
 */
export default function CartPage() {
  const {
    lines, source, degraded, isLoading,
    remove, setQuantity, toggleSelect, selectAll, deselectAll, clear,
    selectedLines, allSelected, selectedTotalCents, selectedCount,
  } = useCartModel();
  const [error, setError] = useState<string | null>(null);

  const run = async (fn: () => Promise<void>) => {
    setError(null);
    try { await fn(); } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '操作失败，请重试');
    }
  };

  return (
    <div className="mx-auto max-w-3xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-light text-ink-black">购物车</h1>
        {lines.length > 0 && (
          <button
            onClick={() => run(() => clear())}
            className="text-sm text-warm-400 hover:text-danger transition-colors"
          >
            清空
          </button>
        )}
      </div>

      {/* 数据源 / 降级提示 */}
      {degraded && (
        <p className="mb-4 rounded-lg bg-warm-100 px-3 py-2 text-xs text-warm-600">
          服务端购物车暂不可用，已切换到本机购物车（离线模式）。此模式下提交订单前请刷新重试。
        </p>
      )}
      {error && (
        <p role="alert" className="mb-4 rounded-lg bg-danger/5 px-3 py-2 text-xs text-danger">
          {error}
        </p>
      )}

      {/* ── 加载中 ── */}
      {isLoading && lines.length === 0 && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-28 rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}

      {/* ── 空购物车 ── */}
      {!isLoading && lines.length === 0 && (
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
      {lines.length > 0 && (
        <div className="mb-4 flex items-center gap-3">
          <button
            onClick={() => run(() => (allSelected ? deselectAll() : selectAll()))}
            className="text-sm text-warm-600 hover:text-ink-black transition-colors"
          >
            {allSelected ? '取消全选' : '全选'}
          </button>
          <span className="text-xs text-text-tertiary">
            共 {lines.length} 种商品
          </span>
        </div>
      )}

      {/* ── 购物车列表 ── */}
      {lines.length > 0 && (
        <div className="space-y-3">
          {lines.map((item) => (
            <GlassCard key={item.skuId} className="flex gap-4 p-4" blur="sm">
              {/* 选中框 */}
              <button
                onClick={() => run(() => toggleSelect(item.skuId))}
                aria-label={item.selected ? '取消选择' : '选择'}
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
                  alt={item.spuName}
                  className="h-full w-full object-cover"
                  loading="lazy"
                />
              </div>

              {/* 信息 */}
              <div className="flex-1 min-w-0">
                {/* `spu_name` 为读时回填、fail-open → null：缺失时回落中性文案，
                    不显示 SKU 业务键（业务键虽是 32-hex 而非技术键，仍不宜当商品名展示） */}
                <h3 className="text-sm font-medium text-ink-black truncate">
                  {item.spuName || '商品信息加载中'}
                </h3>
                {item.spec && (
                  <p className="text-xs text-warm-400 mt-0.5">{item.spec}</p>
                )}
                <PriceDisplay cents={item.price} size="sm" className="mt-1.5" />
                {item.createTime && (
                  <p className="text-xs text-text-tertiary mt-1">加入于 {new Date(item.createTime).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })} {new Date(item.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</p>
                )}
              </div>

              {/* 数量 + 删除 */}
              <div className="flex flex-col items-end gap-2">
                <button
                  onClick={() => run(() => remove(item.skuId))}
                  aria-label="删除"
                  className="p-1 text-warm-400 hover:text-danger transition-colors"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
                <div className="flex items-center gap-1 border border-warm-200 rounded-lg">
                  <button
                    onClick={() => run(() => setQuantity(item.skuId, item.quantity - 1))}
                    aria-label="减少数量"
                    className="px-2 py-1 text-warm-600 hover:text-ink-black transition-colors"
                  >
                    <Minus className="h-3 w-3" />
                  </button>
                  <span className="text-sm text-ink-black min-w-[20px] text-center">
                    {item.quantity}
                  </span>
                  <button
                    onClick={() => run(() => setQuantity(item.skuId, item.quantity + 1))}
                    aria-label="增加数量"
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
      {selectedLines.length > 0 && (
        <div className="mt-8 sticky bottom-4">
          <GlassCard className="flex items-center justify-between p-5" blur="lg">
            <div>
              <p className="text-xs text-warm-600">
                已选 {selectedLines.length} 件，共 {selectedCount} 件
              </p>
              <PriceDisplay cents={selectedTotalCents} size="lg" className="mt-1" />
            </div>
            {degraded ? (
              <Button variant="secondary" size="lg" disabled title="离线模式不可结算">
                离线模式
              </Button>
            ) : (
              <Link href="/shop/checkout">
                <Button variant="gold" size="lg">
                  去结算
                </Button>
              </Link>
            )}
          </GlassCard>
        </div>
      )}

      {/* 数据源注记（对开发者可见，避免「本地加购却下不了单」再次被误判为后端缺陷） */}
      {source === 'local' && !degraded && lines.length > 0 && (
        <p className="mt-6 text-center text-xs text-text-tertiary">
          当前为未登录状态，购物车保存在本机；登录后会自动并入服务端购物车。
        </p>
      )}
    </div>
  );
}

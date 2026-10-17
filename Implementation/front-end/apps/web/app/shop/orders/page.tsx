'use client';

import { useState } from 'react';
import { useOrders, useCancelOrder, useConfirmOrder } from '@icedmall/api';
import { GlassCard, PriceDisplay, Button } from '@icedmall/ui';
import { ORDER_STATUS_CODE, orderStatusColorClass, orderStatusText } from '@icedmall/utils';
import { OrderItemRow } from '@/app/components/order-item-row';
import { LoadFailedState } from '@/app/components/load-failed-state';
import Link from 'next/link';

/**
 * 订单列表 — `/api/trade/order/page`。
 *
 * ── 2026-10 B10：状态文案改显式枚举映射（**这是一次静默失效的修复**）──────────
 * 旧实现：`ORDER_STATUS_TEXT[order.status] ?? order.status`，而 `ORDER_STATUS_TEXT` 当时是
 * `Record<string, string>`（键 = `PENDING_PAY` 这类**枚举名**），`order.status` 是**数字**
 * ⇒ 恒 `undefined` ⇒ 页面把「待付款」显示成裸数字「1」，**不报错**。
 * 现在的口径：文案统一走 `orderStatusText()`（`packages/utils/order-status.ts`，键域是
 * 后端 `OrderStatusEnum` 的数字码，漏值编译期报错，表外取值显示原始值）。
 * 标签页同样用 `ORDER_STATUS_CODE` 常量，不再写 `1 / 2 / 3` 这类魔数。
 *
 * ── 失败态 ──────────────────────────────────────────────────
 * 旧实现只判 `isLoading` 与 `records.length`：请求失败被渲染成「暂无订单」，
 * 与「确实没有订单」不可分辨。现在 `isError` 走 `LoadFailedState`（语义色 + 重试）。
 */

const TABS = [
  { label: '全部', value: undefined },
  { label: '待付款', value: ORDER_STATUS_CODE.PENDING_PAYMENT },
  { label: '待发货', value: ORDER_STATUS_CODE.PENDING_SHIPMENT },
  { label: '待收货', value: ORDER_STATUS_CODE.PENDING_RECEIPT },
  { label: '已完成', value: ORDER_STATUS_CODE.COMPLETED },
  { label: '待评价', value: ORDER_STATUS_CODE.PENDING_REVIEW },
] as const;

export default function OrdersPage() {
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<number | undefined>();
  const { data, isLoading, isError, error, isFetching, refetch } = useOrders({ page, size: 10, status });
  const cancelOrder = useCancelOrder();
  const confirmOrder = useConfirmOrder();

  return (
    <div className="mx-auto max-w-3xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-6">我的订单</h1>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 overflow-x-auto">
        {TABS.map((t) => (
          <button key={t.label} onClick={() => { setStatus(t.value); setPage(1); }}
            aria-pressed={status === t.value}
            className={`shrink-0 px-4 py-1.5 text-sm rounded-full border transition-all ${
              status === t.value ? 'border-accent-gold bg-accent-gold/5 text-ink-black' : 'border-warm-200 text-warm-600 hover:border-warm-400'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* 失败态（先于空态判定 —— 错误绝不能落到"暂无订单"）*/}
      {isError && (
        <LoadFailedState
          title="订单加载失败"
          description="没能获取到订单列表（不是「没有订单」）。请重试。"
          detail={error instanceof Error ? error.message : undefined}
          onRetry={() => { void refetch(); }}
          retrying={isFetching}
        />
      )}

      {isLoading && !isError && (
        <div className="space-y-3">
          {[1,2,3].map(i => <div key={i} className="h-24 rounded-2xl bg-warm-100 animate-glass-shimmer" />)}
        </div>
      )}

      {/* 空态（只有"确实没有数据"才走这里）*/}
      {!isLoading && !isError && data?.records?.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl">📋</div>
          <p className="mt-4 text-warm-600">
            {status === undefined ? '暂无订单' : '该状态下暂无订单'}
          </p>
          <Link href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">去逛逛 →</Link>
        </div>
      )}

      <div className="space-y-3">
        {!isError && data?.records?.map((order) => (
          <Link key={order.order_no} href={`/shop/orders/${order.order_no}`}>
            <GlassCard className="p-4 hover:shadow-glass-lg transition-shadow" blur="sm">
              <div className="flex items-center justify-between mb-2">
                <p className="text-xs text-warm-400">{order.order_no}</p>
                {/* 状态文案唯一出口：显式枚举映射 + 表外取值回落原始值 */}
                <span className={`text-xs px-2 py-0.5 rounded-full ${orderStatusColorClass(order.status)}`}>
                  {orderStatusText(order.status)}
                </span>
              </div>
              <div className="space-y-2">
                {order.items?.slice(0, 3).map((item) => (
                  // 订单项明细（含比价/失效的**降级态**渲染）：与订单详情页共用同一渲染路径，
                  // 保证「无比价字段 → 只显快照，不报错、不缩行、不骨架屏、不回退技术 id」
                  <OrderItemRow key={item.id} item={item} variant="list" />
                ))}
                {order.items && order.items.length > 3 && (
                  <p className="text-xs text-warm-400">...共 {order.items.length} 件</p>
                )}
              </div>
              <div className="flex items-center justify-between mt-3 pt-3 border-t border-warm-100">
                <PriceDisplay cents={order.total_amount} size="sm" />
                <div className="flex gap-2" onClick={(e) => e.preventDefault()}>
                  {order.status === ORDER_STATUS_CODE.PENDING_PAYMENT && (
                    <Button size="sm" variant="secondary"
                      onClick={() => cancelOrder.mutate(order.order_no)}>取消</Button>
                  )}
                  {order.status === ORDER_STATUS_CODE.PENDING_RECEIPT && (
                    <Button size="sm" variant="primary"
                      onClick={() => confirmOrder.mutate(order.order_no)}>确认收货</Button>
                  )}
                </div>
              </div>
            </GlassCard>
          </Link>
        ))}
      </div>

      {/* 分页 */}
      {!isError && data && data.pages > 1 && (
        <div className="flex justify-center gap-2 mt-8">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 disabled:opacity-30">上一页</button>
          <span className="px-3 py-1.5 text-sm text-warm-600">{page}/{data.pages}</span>
          <button onClick={() => setPage((p) => Math.min(data.pages, p + 1))} disabled={page >= data.pages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 disabled:opacity-30">下一页</button>
        </div>
      )}
    </div>
  );
}

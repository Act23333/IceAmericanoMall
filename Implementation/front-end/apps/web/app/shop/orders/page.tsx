'use client';

import { useState } from 'react';
import { useOrders, useCancelOrder, useConfirmOrder } from '@icedmall/api';
import { GlassCard, PriceDisplay, Button } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import { ORDER_STATUS_TEXT } from '@icedmall/utils';
import Link from 'next/link';

const TABS = [
  { label: '全部', value: undefined },
  { label: '待付款', value: 1 },
  { label: '待发货', value: 2 },
  { label: '待收货', value: 3 },
  { label: '已完成', value: 4 },
];

export default function OrdersPage() {
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<number | undefined>();
  const { data, isLoading } = useOrders({ page, size: 10, status });
  const cancelOrder = useCancelOrder();
  const confirmOrder = useConfirmOrder();

  return (
    <div className="mx-auto max-w-3xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-6">我的订单</h1>

      {/* Tabs */}
      <div className="flex gap-2 mb-6 overflow-x-auto">
        {TABS.map((t) => (
          <button key={t.label} onClick={() => { setStatus(t.value as any); setPage(1); }}
            className={`shrink-0 px-4 py-1.5 text-sm rounded-full border transition-all ${
              status === t.value ? 'border-accent-gold bg-accent-gold/5 text-ink-black' : 'border-warm-200 text-warm-600 hover:border-warm-400'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {isLoading && (
        <div className="space-y-3">
          {[1,2,3].map(i => <div key={i} className="h-24 rounded-2xl bg-warm-100 animate-glass-shimmer" />)}
        </div>
      )}

      {data?.records?.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl">📋</div>
          <p className="mt-4 text-warm-600">暂无订单</p>
          <Link href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">去逛逛 →</Link>
        </div>
      )}

      <div className="space-y-3">
        {data?.records?.map((order) => (
          <Link key={order.orderNo} href={`/shop/orders/${order.orderNo}`}>
            <GlassCard className="p-4 hover:shadow-glass-lg transition-shadow" blur="sm">
              <div className="flex items-center justify-between mb-2">
                <p className="text-xs text-warm-400">{order.orderNo}</p>
                <span className="text-xs px-2 py-0.5 rounded-full bg-accent-green/10 text-accent-green-dark">
                  {ORDER_STATUS_TEXT[order.status] ?? order.status}
                </span>
              </div>
              <div className="space-y-2">
                {order.items?.slice(0, 3).map((item) => (
                  <div key={item.id} className="flex items-center gap-3">
                    <div className="h-14 w-14 shrink-0 rounded-lg bg-warm-100 overflow-hidden">
                      <img src={item.image} alt={item.productName} className="h-full w-full object-cover" loading="lazy" decoding="async" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-ink-black truncate">{item.productName}</p>
                      <p className="text-xs text-warm-400">{item.skuSpec} x{item.quantity}</p>
                    </div>
                    <p className="text-sm text-ink-soft">¥{formatPrice(item.price)}</p>
                  </div>
                ))}
                {order.items && order.items.length > 3 && (
                  <p className="text-xs text-warm-400">...共 {order.items.length} 件</p>
                )}
              </div>
              <div className="flex items-center justify-between mt-3 pt-3 border-t border-warm-100">
                <PriceDisplay cents={order.totalAmount} size="sm" />
                <div className="flex gap-2" onClick={(e) => e.preventDefault()}>
                  {order.status === 1 && (
                    <Button size="sm" variant="secondary"
                      onClick={() => cancelOrder.mutate(order.orderNo)}>取消</Button>
                  )}
                  {order.status === 3 && (
                    <Button size="sm" variant="primary"
                      onClick={() => confirmOrder.mutate(order.orderNo)}>确认收货</Button>
                  )}
                </div>
              </div>
            </GlassCard>
          </Link>
        ))}
      </div>

      {/* 分页 */}
      {data && data.pages > 1 && (
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

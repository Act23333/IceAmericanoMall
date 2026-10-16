'use client';

import { formatPrice } from '@icedmall/utils';
import type { OrderItemVO } from '@icedmall/api';
import { orderItemCompareHint, orderItemDisplayName } from './order-item-compare';

/**
 * 订单项明细行 —— 订单列表与订单详情**共用**同一渲染路径。
 *
 * 共用的理由（不只是去重）：比价/失效提示的**降级态**必须「不缩行、不报错、
 * 不骨架屏卡死、不回退技术 id」（ADR-061 §决策 3）。两条渲染路径若各写一份，
 * 这个不变式就有两处会漂移；合并为单一组件后，`orderItemCompareHint()` 的
 * 降级分支同时约束列表与详情。
 *
 * 快照字段（下单时点冻结，**绝不回查实时表**）：`spu_name` / `sku_spec` / `price` /
 * `quantity` / `image` —— 即使商品已下架或 SKU 已删除，这些值照常渲染。
 */
export function OrderItemRow({
  item,
  variant = 'detail',
}: {
  item: OrderItemVO;
  variant?: 'list' | 'detail';
}) {
  const hint = orderItemCompareHint(item);
  const name = orderItemDisplayName(item);

  return (
    <div
      className={
        variant === 'detail'
          ? 'flex items-center gap-3 py-2 border-b border-warm-100 last:border-0'
          : 'flex items-center gap-3'
      }
    >
      <div className="h-14 w-14 shrink-0 rounded-lg bg-warm-100 overflow-hidden">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={item.image} alt={name} className="h-full w-full object-cover" loading="lazy" decoding="async" />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm text-ink-black truncate">{name}</p>
        <p className="text-xs text-warm-400">
          {item.sku_spec} x{item.quantity}
        </p>
        {/*
          比价提示：`null` = 不渲染（含「无法比对」/「缺失」两种降级态 —— 同款渲染）。
          提示文案恒不含任何 id（`order-item-compare.ts` 保证）。
        */}
        {hint && (
          <p
            className={`mt-0.5 text-xs ${hint.tone === 'warning' ? 'text-danger' : 'text-text-tertiary'}`}
            data-compare-state-note
          >
            {hint.text}
          </p>
        )}
      </div>

      {variant === 'detail' ? (
        <div className="text-right">
          <p className="text-sm text-ink-black">¥{formatPrice(item.price)}</p>
          <p className="text-xs text-warm-400">x{item.quantity}</p>
        </div>
      ) : (
        <p className="text-sm text-ink-soft">¥{formatPrice(item.price)}</p>
      )}
    </div>
  );
}

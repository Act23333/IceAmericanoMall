'use client';

import { useParams } from 'next/navigation';
import { useOrder, useCancelOrder, useConfirmOrder } from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice, ORDER_STATUS_CODE, orderStatusColorClass, orderStatusText } from '@icedmall/utils';
import { OrderItemRow } from '@/app/components/order-item-row';
import { LoadFailedState } from '@/app/components/load-failed-state';
import Link from 'next/link';

/**
 * 订单详情 — `/api/trade/order/{orderNo}`。
 *
 * ── 2026-10 B10 变更 ────────────────────────────────────────────
 * 1. **状态文案**：旧 `ORDER_STATUS_TEXT[order.status] ?? order.status` 是「数字下标索引
 *    枚举名键表」⇒ 恒 `undefined` ⇒ 显示裸数字。现统一走 `orderStatusText()`
 *    （键域 = 后端 `OrderStatusEnum` 数字码；表外取值显示 `状态 7`，不静默变空）。
 * 2. **状态判定**魔数 `order.status === 1 / 3 / 4` → `ORDER_STATUS_CODE.*`。
 * 3. **失败态**：旧实现只有 `isLoading` 与「订单未找到」两条分支 ⇒ 请求失败（网关不可达 /
 *    5xx / 401）会落到「订单未找到」，把**加载失败**说成**订单不存在**。现拆三支：
 *    失败（可重试）/ 不存在 / 正常。
 */
export default function OrderDetailPage() {
  const params = useParams();
  const orderNo = params.orderNo as string;
  const { data: order, isLoading, isError, error, isFetching, refetch } = useOrder(orderNo);
  const cancelOrder = useCancelOrder();
  const confirmOrder = useConfirmOrder();

  if (isLoading) {
    return <div className="mx-auto max-w-2xl px-4 pt-24 pb-20"><div className="h-64 rounded-2xl bg-warm-100 animate-glass-shimmer" /></div>;
  }

  // 失败 ≠ 不存在：失败必须可重试，且文案不得说「未找到」
  if (isError) {
    return (
      <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
        <LoadFailedState
          title="订单详情加载失败"
          description="没能获取到该订单（不是「订单不存在」）。请重试，或返回列表。"
          detail={error instanceof Error ? error.message : undefined}
          onRetry={() => { void refetch(); }}
          retrying={isFetching}
        />
        <div className="mt-4 text-center">
          <Link href="/shop/orders" className="text-sm text-accent-green hover:underline">返回订单列表</Link>
        </div>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="text-center pt-24 pb-20">
        <div className="text-6xl">🔍</div><h2 className="mt-4 text-lg text-ink-black">订单未找到</h2>
        <p className="mt-2 text-sm text-warm-600">该订单不存在，或不属于当前账号。</p>
        <Link href="/shop/orders" className="mt-4 inline-block text-sm text-accent-green">返回订单列表</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-light text-ink-black">订单详情</h1>
        <span className={`text-xs px-2 py-0.5 rounded-full ${orderStatusColorClass(order.status)}`}>
          {orderStatusText(order.status)}
        </span>
      </div>

      <GlassCard className="p-4 mb-4" blur="sm">
        <p className="text-xs text-warm-400">订单号: {order.order_no}</p>
        <p className="text-xs text-warm-400 mt-1">创建时间: {order.create_time}</p>
      </GlassCard>

      {/* 收货信息 */}
      <GlassCard className="p-4 mb-4" blur="sm">
        <h3 className="text-sm font-medium text-ink-black mb-2">收货信息</h3>
        <p className="text-sm">{order.receiver_name} {order.receiver_phone}</p>
        <p className="text-xs text-warm-600 mt-1">{order.receiver_address}</p>
      </GlassCard>

      {/* 商品清单 */}
      <GlassCard className="p-4 mb-4" blur="sm">
        <h3 className="text-sm font-medium text-ink-black mb-3">商品清单</h3>
        {order.items?.map((item) => (
          // 订单项 = 业务键标识 + 快照；比价/失效为读时增强、fail-open。
          // 后端**未**出线格式比价字段（契约 §十二 12.2）⇒ 本批不消费任何比价字段，
          // 但渲染走与「无法比对」**同款**路径（`OrderItemRow` → `orderItemCompareHint`）：
          // 只显快照，不报错、不缩行、不骨架屏卡死、不回退显示技术 id，也不把缺失当「已下架」。
          <OrderItemRow key={item.id} item={item} variant="detail" />
        ))}
      </GlassCard>

      {/* 金额 */}
      <GlassCard className="p-4 mb-6" blur="sm">
        <div className="space-y-1 text-sm">
          <div className="flex justify-between"><span className="text-warm-600">商品总额</span><span>¥{formatPrice(order.total_amount)}</span></div>
          <div className="flex justify-between"><span className="text-warm-600">优惠</span><span>-¥{formatPrice(order.discount_amount)}</span></div>
          <div className="flex justify-between"><span className="text-warm-600">运费</span><span>¥{formatPrice(order.post_fee ?? 0)}</span></div>
          <div className="flex justify-between pt-2 border-t border-warm-100"><span className="font-medium">实付</span><PriceDisplay cents={order.pay_amount} size="sm" /></div>
        </div>
      </GlassCard>

      {/* 操作 */}
      <div className="flex gap-3">
        {order.status === ORDER_STATUS_CODE.PENDING_PAYMENT && (
          <>
            <Link href={`/shop/pay/${order.order_no}`} className="flex-1">
              <Button variant="primary" size="md" className="w-full">去支付</Button>
            </Link>
            <Button variant="secondary" size="md" onClick={() => cancelOrder.mutate(order.order_no)}>取消订单</Button>
          </>
        )}
        {order.status === ORDER_STATUS_CODE.PENDING_RECEIPT && (
          <Button variant="primary" size="md" onClick={() => confirmOrder.mutate(order.order_no)}>确认收货</Button>
        )}
        {order.status === ORDER_STATUS_CODE.COMPLETED && (
          <Link href={`/shop/after-sale?orderNo=${order.order_no}`} className="flex-1">
            <Button variant="secondary" size="md" className="w-full">申请售后</Button>
          </Link>
        )}
      </div>
    </div>
  );
}

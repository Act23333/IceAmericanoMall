'use client';

import { useParams } from 'next/navigation';
import { useOrder, useCancelOrder, useConfirmOrder } from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice, ORDER_STATUS_TEXT } from '@icedmall/utils';
import Link from 'next/link';

export default function OrderDetailPage() {
  const params = useParams();
  const orderNo = params.orderNo as string;
  const { data: order, isLoading } = useOrder(orderNo);
  const cancelOrder = useCancelOrder();
  const confirmOrder = useConfirmOrder();

  if (isLoading) {
    return <div className="mx-auto max-w-2xl px-4 pt-24 pb-20"><div className="h-64 rounded-2xl bg-warm-100 animate-glass-shimmer" /></div>;
  }
  if (!order) {
    return (
      <div className="text-center pt-24 pb-20">
        <div className="text-6xl">🔍</div><h2 className="mt-4 text-lg text-ink-black">订单未找到</h2>
        <Link href="/shop/orders" className="mt-4 inline-block text-sm text-accent-green">返回订单列表</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-light text-ink-black">订单详情</h1>
        <span className="text-xs px-2 py-0.5 rounded-full bg-accent-green/10 text-accent-green-dark">
          {ORDER_STATUS_TEXT[order.status] ?? order.status}
        </span>
      </div>

      <GlassCard className="p-4 mb-4" blur="sm">
        <p className="text-xs text-warm-400">订单号: {order.orderNo}</p>
        <p className="text-xs text-warm-400 mt-1">创建时间: {order.createTime}</p>
      </GlassCard>

      {/* 收货信息 */}
      <GlassCard className="p-4 mb-4" blur="sm">
        <h3 className="text-sm font-medium text-ink-black mb-2">收货信息</h3>
        <p className="text-sm">{order.receiverName} {order.receiverPhone}</p>
        <p className="text-xs text-warm-600 mt-1">{order.receiverAddress}</p>
      </GlassCard>

      {/* 商品清单 */}
      <GlassCard className="p-4 mb-4" blur="sm">
        <h3 className="text-sm font-medium text-ink-black mb-3">商品清单</h3>
        {order.items?.map((item) => (
          <div key={item.id} className="flex items-center gap-3 py-2 border-b border-warm-100 last:border-0">
            <div className="h-14 w-14 shrink-0 rounded-lg bg-warm-100 overflow-hidden">
              <img src={item.image} alt={item.productName} className="h-full w-full object-cover" loading="lazy" decoding="async" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-ink-black truncate">{item.productName}</p>
              <p className="text-xs text-warm-400">{item.skuSpec}</p>
            </div>
            <div className="text-right">
              <p className="text-sm text-ink-black">¥{formatPrice(item.price)}</p>
              <p className="text-xs text-warm-400">x{item.quantity}</p>
            </div>
          </div>
        ))}
      </GlassCard>

      {/* 金额 */}
      <GlassCard className="p-4 mb-6" blur="sm">
        <div className="space-y-1 text-sm">
          <div className="flex justify-between"><span className="text-warm-600">商品总额</span><span>¥{formatPrice(order.totalAmount)}</span></div>
          <div className="flex justify-between"><span className="text-warm-600">优惠</span><span>-¥{formatPrice(order.discountAmount)}</span></div>
          <div className="flex justify-between pt-2 border-t border-warm-100"><span className="font-medium">实付</span><PriceDisplay cents={order.payAmount} size="sm" /></div>
        </div>
      </GlassCard>

      {/* 操作 */}
      <div className="flex gap-3">
        {order.status === 1 && (
          <>
            <Link href={`/shop/pay/${order.orderNo}`} className="flex-1">
              <Button variant="primary" size="md" className="w-full">去支付</Button>
            </Link>
            <Button variant="secondary" size="md" onClick={() => cancelOrder.mutate(order.orderNo)}>取消订单</Button>
          </>
        )}
        {order.status === 3 && (
          <Button variant="primary" size="md" onClick={() => confirmOrder.mutate(order.orderNo)}>确认收货</Button>
        )}
        {order.status === 4 && (
          <Link href={`/shop/after-sale?orderNo=${order.orderNo}`} className="flex-1">
            <Button variant="secondary" size="md" className="w-full">申请售后</Button>
          </Link>
        )}
      </div>
    </div>
  );
}

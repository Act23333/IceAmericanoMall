'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useCartStore } from '@/app/stores/cart-store';
import { useAddresses, useCreateOrder } from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import Link from 'next/link';

export default function CheckoutPage() {
  const router = useRouter();
  const { items } = useCartStore();
  const selected = items.filter((i) => i.selected);
  const { data: addresses } = useAddresses();
  const createOrder = useCreateOrder();

  const [addressId, setAddressId] = useState<number | null>(null);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const totalCents = selected.reduce((s, i) => s + i.price * i.quantity, 0);
  const defaultAddr = addresses?.find((a) => a.defaulted) ?? addresses?.[0];

  const handleSubmit = async () => {
    if (selected.length === 0) return;
    if (!addressId && !defaultAddr) {
      alert('请先添加收货地址');
      return;
    }
    setSubmitting(true);
    try {
      const order = await createOrder.mutateAsync({
        addressId: addressId ?? defaultAddr!.id,
        cartItemIds: selected.map((i) => i.skuId),
        remark: remark || undefined,
      });
      router.push(`/shop/pay/${order.orderNo}`);
    } catch (e: any) {
      alert(e?.message ?? '下单失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (selected.length === 0) {
    return (
      <div className="mx-auto max-w-2xl px-4 pt-24 pb-20 text-center">
        <div className="text-6xl">📦</div>
        <h2 className="mt-4 text-lg font-medium text-ink-black">没有可结算的商品</h2>
        <p className="mt-2 text-sm text-warm-600">请先在购物车中选择商品</p>
        <Link href="/shop/cart" className="mt-6 inline-block text-accent-green text-sm">返回购物车 →</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-8">确认订单</h1>

      {/* 收货地址 */}
      <section className="mb-8">
        <h2 className="text-sm font-medium text-ink-soft mb-3">收货地址</h2>
        {addresses && addresses.length > 0 ? (
          <div className="space-y-2">
            {addresses.map((a) => (
              <label key={a.id} className={`block p-4 rounded-xl border-2 cursor-pointer transition-colors ${
                (addressId ?? defaultAddr?.id) === a.id
                  ? 'border-accent-green bg-accent-green/5'
                  : 'border-warm-200 hover:border-warm-400'
              }`}>
                <input type="radio" name="address" value={a.id}
                  checked={(addressId ?? defaultAddr?.id) === a.id}
                  onChange={() => setAddressId(a.id)} className="sr-only" />
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm font-medium text-ink-black">{a.receiver} {a.phone}</p>
                    <p className="text-xs text-warm-600 mt-1">{a.province} {a.city} {a.district} {a.street} {a.detail}</p>
                  </div>
                  {a.defaulted && <span className="text-xs text-accent-green">默认</span>}
                </div>
              </label>
            ))}
          </div>
        ) : (
          <Link href="/user/addresses" className="text-sm text-accent-green">+ 添加收货地址</Link>
        )}
      </section>

      {/* 商品列表 */}
      <section className="mb-8">
        <h2 className="text-sm font-medium text-ink-soft mb-3">商品清单</h2>
        <div className="space-y-2">
          {selected.map((item) => (
            <GlassCard key={item.skuId} className="flex gap-3 p-3" blur="sm">
              <div className="h-16 w-16 shrink-0 rounded-lg bg-warm-100 overflow-hidden">
                <img src={item.image} alt={item.productName} className="h-full w-full object-cover" loading="lazy" decoding="async" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm text-ink-black truncate">{item.productName}</p>
                {item.spec && <p className="text-xs text-warm-400">{item.spec}</p>}
                <PriceDisplay cents={item.price} size="sm" />
              </div>
              <div className="text-sm text-ink-soft">x{item.quantity}</div>
            </GlassCard>
          ))}
        </div>
      </section>

      {/* 备注 */}
      <section className="mb-8">
        <input type="text" value={remark} onChange={(e) => setRemark(e.target.value)}
          placeholder="订单备注（选填）" maxLength={100}
          className="w-full rounded-xl border border-warm-200 bg-white px-4 py-2.5 text-sm outline-none transition-colors focus:border-accent-green focus:ring-2 focus:ring-accent-green/20 placeholder:text-warm-400"
        />
      </section>

      {/* 结算 */}
      <GlassCard className="flex items-center justify-between p-5" blur="lg">
        <div>
          <p className="text-xs text-warm-600">{selected.length} 件商品</p>
          <PriceDisplay cents={totalCents} size="lg" />
        </div>
        <Button onClick={handleSubmit} variant="gold" size="lg" loading={submitting}>提交订单</Button>
      </GlassCard>
    </div>
  );
}

'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useCartStore } from '@/app/stores/cart-store';
import { useAddresses, useCreateOrder, useCouponFilter, useAvailableCoupons } from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import { Ticket, ChevronDown, ChevronUp, Check } from 'lucide-react';
import Link from 'next/link';

/**
 * 结算页 — V5.0 增强版
 *
 * 功能:
 *   1. 收货地址选择 (inline, 不跳转)
 *   2. 商品清单
 *   3. 优惠券选择 (V4.3 预过滤 + 多券叠加)
 *   4. 订单备注
 *   5. 金额汇总 → 提交订单
 */
export default function CheckoutPage() {
  const router = useRouter();
  const { items } = useCartStore();
  const selected = items.filter((i) => i.selected);
  const { data: addresses } = useAddresses();
  const { data: allCoupons } = useAvailableCoupons();
  const couponFilter = useCouponFilter();
  const createOrder = useCreateOrder();

  const [addressId, setAddressId] = useState<number | null>(null);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [showCoupons, setShowCoupons] = useState(false);
  const [selectedCouponIds, setSelectedCouponIds] = useState<number[]>([]);
  const [filteredCoupons, setFilteredCoupons] = useState<any[]>([]);

  const totalCents = selected.reduce((s, i) => s + i.price * i.quantity, 0);
  const skuIds = selected.map((i) => Number(i.skuId));
  const defaultAddr = addresses?.find((a) => a.defaulted) ?? addresses?.[0];

  // 预过滤可用券
  useEffect(() => {
    if (skuIds.length > 0 && allCoupons && allCoupons.length > 0) {
      couponFilter.mutate(
        { skuIds, totalAmount: totalCents },
        { onSuccess: (data) => setFilteredCoupons(data) }
      );
    }
  }, [items]);

  // 计算优惠金额
  const discountCents = selectedCouponIds.reduce((sum, id) => {
    const c = filteredCoupons.find((f: any) => f.id === id);
    return sum + (c?.value ?? 0);
  }, 0);
  const payCents = Math.max(0, totalCents - discountCents);

  const toggleCoupon = (id: number) => {
    setSelectedCouponIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

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
        cartItemIds: selected.map((i) => Number(i.skuId)),
        userCouponIds: selectedCouponIds.length > 0 ? selectedCouponIds : undefined,
        remark: remark || undefined,
      } as any);
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
        <div className="text-6xl select-none">📦</div>
        <h2 className="mt-4 text-lg font-medium text-ink-black">没有可结算的商品</h2>
        <p className="mt-2 text-sm text-text-secondary">请先在购物车中选择商品</p>
        <Link href="/shop/cart" className="mt-6 inline-block text-sm text-accent hover:underline">
          返回购物车 →
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      {/* 步骤指示器 */}
      <div className="flex items-center gap-3 mb-8 text-sm">
        <span className="flex items-center gap-1.5 font-medium text-accent">
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-accent text-white text-xs">1</span>
          确认订单
        </span>
        <span className="text-warm-gray-400">→</span>
        <span className="text-text-tertiary">2 支付</span>
        <span className="text-warm-gray-400">→</span>
        <span className="text-text-tertiary">3 完成</span>
      </div>

      <h1 className="text-2xl font-light text-ink-black mb-8">确认订单</h1>

      {/* ── 收货地址 ── */}
      <section className="mb-8">
        <h2 className="text-sm font-medium text-text-secondary mb-3">收货地址</h2>
        {addresses && addresses.length > 0 ? (
          <div className="space-y-2">
            {addresses.map((a) => (
              <label
                key={a.id}
                className={`block p-4 rounded-xl border-2 cursor-pointer transition-all duration-200 ${
                  (addressId ?? defaultAddr?.id) === a.id
                    ? 'border-accent bg-accent-light/50'
                    : 'border-warm-gray-200 hover:border-warm-gray-400'
                }`}
              >
                <input
                  type="radio"
                  name="address"
                  value={a.id}
                  checked={(addressId ?? defaultAddr?.id) === a.id}
                  onChange={() => setAddressId(a.id)}
                  className="sr-only"
                />
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-sm font-medium text-ink-black">
                      {a.receiver} <span className="text-text-tertiary font-normal">{a.phone}</span>
                    </p>
                    <p className="text-xs text-text-secondary mt-1">
                      {a.province} {a.city} {a.district} {a.street} {a.detail}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    {a.label && (
                      <span className="text-xs px-1.5 py-0.5 rounded bg-warm-gray-100 text-text-tertiary">
                        {a.label}
                      </span>
                    )}
                    {a.defaulted && (
                      <span className="text-xs text-accent font-medium">默认</span>
                    )}
                  </div>
                </div>
              </label>
            ))}
          </div>
        ) : (
          <Link href="/user/addresses" className="text-sm text-accent hover:underline">
            + 添加收货地址
          </Link>
        )}
      </section>

      {/* ── 商品清单 ── */}
      <section className="mb-6">
        <h2 className="text-sm font-medium text-text-secondary mb-3">商品清单</h2>
        <div className="space-y-2">
          {selected.map((item) => (
            <GlassCard key={item.skuId} className="flex gap-3 p-3" blur="sm">
              <div className="h-16 w-16 shrink-0 rounded-lg bg-warm-gray-100 overflow-hidden">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={item.image}
                  alt={item.productName}
                  className="h-full w-full object-cover"
                  loading="lazy"
                  decoding="async"
                />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm text-ink-black truncate">{item.productName}</p>
                {item.spec && <p className="text-xs text-text-tertiary">{item.spec}</p>}
                <PriceDisplay cents={item.price} size="sm" />
              </div>
              <div className="text-sm text-text-secondary">×{item.quantity}</div>
            </GlassCard>
          ))}
        </div>
      </section>

      {/* ── 优惠券 ── */}
      <section className="mb-6">
        <button
          onClick={() => setShowCoupons(!showCoupons)}
          className="flex w-full items-center justify-between text-sm font-medium text-text-secondary mb-3 hover:text-ink-black transition-colors"
        >
          <span className="flex items-center gap-1.5">
            <Ticket className="h-4 w-4" />
            优惠券
            {selectedCouponIds.length > 0 && (
              <span className="text-xs text-accent">(已选 {selectedCouponIds.length} 张)</span>
            )}
          </span>
          {showCoupons ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>
        {showCoupons && (
          <div className="space-y-2 animate-fade-in">
            {filteredCoupons.length > 0 ? (
              filteredCoupons.map((c: any) => {
                const isSelected = selectedCouponIds.includes(c.id);
                return (
                  <button
                    key={c.id}
                    onClick={() => toggleCoupon(c.id)}
                    className={`flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-all duration-200 ${
                      isSelected
                        ? 'border-accent bg-accent-light/30'
                        : 'border-warm-gray-200 hover:border-warm-gray-400'
                    }`}
                  >
                    <span
                      className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition-colors ${
                        isSelected
                          ? 'border-accent bg-accent text-white'
                          : 'border-warm-gray-300'
                      }`}
                    >
                      {isSelected && <Check className="h-3 w-3" />}
                    </span>
                    <div className="flex-1 text-left">
                      <p className="text-sm text-ink-black">{c.name || `满${formatPrice(c.minAmount)}减${formatPrice(c.value)}`}</p>
                      <p className="text-xs text-text-tertiary">
                        {c.minAmount > 0 ? `满¥${formatPrice(c.minAmount)}可用` : '无门槛'}
                      </p>
                    </div>
                    <span className="text-sm font-medium text-accent">-¥{formatPrice(c.value)}</span>
                  </button>
                );
              })
            ) : (
              <p className="text-xs text-text-tertiary py-3 text-center">
                {allCoupons ? '暂无可用优惠券' : '加载中...'}
              </p>
            )}
          </div>
        )}
      </section>

      {/* ── 备注 ── */}
      <section className="mb-8">
        <input
          type="text"
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
          placeholder="订单备注（选填，如：请发顺丰）"
          maxLength={100}
          className="w-full rounded-xl border border-warm-gray-200 bg-white px-4 py-2.5 text-sm outline-none transition-all duration-200 focus:border-accent focus:ring-2 focus:ring-accent/20 placeholder:text-text-tertiary"
        />
      </section>

      {/* ── 金额汇总 + 提交 ── */}
      <GlassCard className="p-5 space-y-3" blur="lg">
        <div className="flex justify-between text-sm">
          <span className="text-text-secondary">商品总额</span>
          <span className="text-ink-black">¥{formatPrice(totalCents)}</span>
        </div>
        {discountCents > 0 && (
          <div className="flex justify-between text-sm">
            <span className="text-accent">优惠券</span>
            <span className="text-accent">-¥{formatPrice(discountCents)}</span>
          </div>
        )}
        <div className="flex justify-between text-sm">
          <span className="text-text-secondary">运费</span>
          <span className="text-ink-black">免运费</span>
        </div>
        <div className="border-t border-warm-gray-200 pt-3 flex items-center justify-between">
          <span className="text-sm text-text-secondary">
            共 {selected.length} 件
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-xs text-text-secondary">实付</span>
            <PriceDisplay cents={payCents} size="lg" />
          </div>
        </div>
        <Button
          onClick={handleSubmit}
          variant="gold"
          size="lg"
          className="w-full"
          loading={submitting}
        >
          提交订单
        </Button>
      </GlassCard>
    </div>
  );
}

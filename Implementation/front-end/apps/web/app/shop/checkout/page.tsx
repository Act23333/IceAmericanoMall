'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useCartModel } from '@/app/stores/cart-model';
import { useAddresses, useCreateOrder, useCheckoutCoupons } from '@icedmall/api';
import { Button, GlassCard, PriceDisplay } from '@icedmall/ui';
import { formatPrice, formatCouponFace, estimateCouponDiscountCents } from '@icedmall/utils';
import { Ticket, ChevronDown, ChevronUp, Check } from 'lucide-react';
import Link from 'next/link';

/**
 * 结算页 — V5.0 增强版（2026-10-17 B3：数据源改为服务端购物车）
 *
 * 功能:
 *   1. 收货地址选择 (inline, 不跳转)
 *   2. 商品清单 —— 取自 `useCartModel()`：已登录 = `GET /api/cart` 的**已勾选项**（服务端为准），
 *      未登录 = 本地；离线降级时禁止提交（本地购物车无法被下单端点读取）
 *   3. 优惠券选择（★ 后端 `CreateOrderReq.user_coupon_id` 为**单券标量**，
 *      多券叠加契约尚未支持 → 本页改为单选，见 §4.6 CreateOrderReq）
 *   4. 订单备注
 *   5. 金额汇总 → 提交订单
 *
 * ★ 可用券为**前端本地筛选**：后端不存在 `POST /api/coupon/available/filter`。
 *   现口径 = **单请求** `GET /api/coupon/available`（券面 name/value/min_amount/discount_type
 *   已随 `UserCouponVO` 一并返回，见 useCheckoutCoupons）—— 不再二次请求 /coupon/template 本地 join。
 *
 * ★ 券适用范围预过滤（`scope_type` / `scope_values`，B2 投影已落码）：
 *   `scope_type=3`（指定商品）与购物车 SPU 业务键求交；`null` = 降级 → **按 ALL 放行**。
 *   ⚠️ **这只是体验优化**：核销时服务端 `CouponScope.accepts` 仍做权威校验，
 *      本端预过滤**不是安全边界**（多滤/漏滤都不改变该券能否真正使用）。
 *
 * ★ 券面渲染口径：`value` 的单位随 `discount_type` 变化（1/3=分，2=百分比），
 *   本页**统一经** `formatCouponFace()`（`@icedmall/utils`）渲染 ——
 *   折扣券渲染为 `8.5折`，未知类型渲染为「优惠券」，**不再按 -¥value 满减口径兜底**。
 */
export default function CheckoutPage() {
  const router = useRouter();
  const { selectedLines, selectedTotalCents, selectedCount, source, degraded, isLoading } = useCartModel();
  const { data: addresses } = useAddresses();
  const createOrder = useCreateOrder();

  const [addressId, setAddressId] = useState<number | null>(null);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [showCoupons, setShowCoupons] = useState(false);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);

  const totalCents = selectedTotalCents;
  const defaultAddr = addresses?.find((a) => a.defaulted) ?? addresses?.[0];

  // 购物车涉及的商品 SPU 业务键 → 供 `scope_type=3` 的券范围预过滤求交
  // （本地行在未登录时通常没有 spuId，此时数组为空 ⇒ 预过滤自动放行，不会错滤）
  const cartSpuIds = useMemo(
    () => Array.from(new Set(selectedLines.map((l) => l.spuId).filter((v): v is string => !!v))),
    [selectedLines],
  );

  // 可用券预过滤（单请求 GET /api/coupon/available —— 券面已随 UserCouponVO 返回）
  const { data: couponOptions, isLoading: couponsLoading } = useCheckoutCoupons(totalCents, cartSpuIds);

  const selectedCoupon = couponOptions.find((c) => c.userCouponId === selectedCouponId);
  // 已选券的面额文案（按 discount_type 解释 value 单位；未知类型 → 「优惠券」降级）
  const selectedFace = selectedCoupon
    ? formatCouponFace({
        discountType: selectedCoupon.discountType,
        value: selectedCoupon.value,
        minAmount: selectedCoupon.minAmount,
      })
    : null;
  // 抵扣金额为**展示预估**：口径镜像后端 CouponPricing（下行真值由后端计算）；未知类型 → 0
  const discountCents = selectedCoupon
    ? estimateCouponDiscountCents(
        {
          discountType: selectedCoupon.discountType,
          value: selectedCoupon.value,
          minAmount: selectedCoupon.minAmount,
        },
        totalCents,
      )
    : 0;
  const payCents = Math.max(0, totalCents - discountCents);

  const toggleCoupon = (userCouponId: number) => {
    setSelectedCouponId((prev) => (prev === userCouponId ? null : userCouponId));
  };

  const handleSubmit = async () => {
    if (selectedLines.length === 0) return;
    if (degraded) {
      setSubmitError('服务端购物车不可用（离线降级）。请刷新页面恢复后再提交订单。');
      return;
    }
    if (!addressId && !defaultAddr) {
      setSubmitError('请先添加收货地址');
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    try {
      // 请求体 `CreateOrderReq` = { address_id, user_coupon_id?, remark?, display_currency? }
      // ⚠️ 后端**不接受** `cart_item_ids` —— 下单商品恒取服务端购物车（/api/cart）的**已勾选项**。
      //    本页勾选状态已由 `useCartModel()` 落到 `/api/cart/item/{skuId}/selected`。
      const order = await createOrder.mutateAsync({
        address_id: addressId ?? defaultAddr!.id,
        user_coupon_id: selectedCouponId ?? undefined,
        remark: remark || undefined,
      });
      router.push(`/shop/pay/${order.order_no}`);
    } catch (e: unknown) {
      setSubmitError(e instanceof Error ? e.message : '下单失败');
    } finally {
      setSubmitting(false);
    }
  };

  // ── 空态：已选 0 件（含加载中） ──
  if (selectedLines.length === 0) {
    return (
      <div className="mx-auto max-w-2xl px-4 pt-24 pb-20 text-center">
        <div className="text-6xl select-none">📦</div>
        {isLoading ? (
          <>
            <h2 className="mt-4 text-lg font-medium text-ink-black">正在加载购物车…</h2>
            <p className="mt-2 text-sm text-text-secondary">请稍候</p>
          </>
        ) : (
          <>
            <h2 className="mt-4 text-lg font-medium text-ink-black">没有可结算的商品</h2>
            <p className="mt-2 text-sm text-text-secondary">
              下单只结算服务端购物车中<strong>已勾选</strong>的商品，请先在购物车中勾选
            </p>
            <Link href="/shop/cart" className="mt-6 inline-block text-sm text-accent hover:underline">
              返回购物车 →
            </Link>
          </>
        )}
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
          {selectedLines.map((item) => (
            <GlassCard key={item.skuId} className="flex gap-3 p-3" blur="sm">
              <div className="h-16 w-16 shrink-0 rounded-lg bg-warm-gray-100 overflow-hidden">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={item.image}
                  alt={item.spuName}
                  className="h-full w-full object-cover"
                  loading="lazy"
                  decoding="async"
                />
              </div>
              <div className="flex-1 min-w-0">
                {/* `spu_name` 回填 fail-open → null：回落中性文案，不显示任何 id */}
                <p className="text-sm text-ink-black truncate">{item.spuName || '商品信息加载中'}</p>
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
            {selectedCouponId !== null && (
              <span className="text-xs text-accent">
                (已选 1 张{selectedFace && !selectedFace.isUnknown ? ` · ${selectedFace.amountText}` : ''})
              </span>
            )}
          </span>
          {showCoupons ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>
        {showCoupons && (
          <div className="space-y-2 animate-fade-in">
            {(couponOptions?.length ?? 0) > 0 ? (
              couponOptions!.map((c) => {
                const isSelected = selectedCouponId === c.userCouponId;
                // 面额按 discount_type 解释（1/3=分 → -¥30.00；2=百分比 → 8.5折；未知 → 「优惠券」）
                const face = formatCouponFace({
                  discountType: c.discountType,
                  value: c.value,
                  minAmount: c.minAmount,
                });
                return (
                  <button
                    key={c.userCouponId}
                    onClick={() => toggleCoupon(c.userCouponId)}
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
                      {/* 券名缺失（模板已删/降级）时用面额兜底名，不再假定满减口径 */}
                      <p className="text-sm text-ink-black">{c.name || face.fallbackName}</p>
                      <p className="text-xs text-text-tertiary">{face.thresholdText}</p>
                    </div>
                    <span className="text-sm font-medium text-accent">{face.deductionText}</span>
                  </button>
                );
              })
            ) : (
              <p className="text-xs text-text-tertiary py-3 text-center">
                {couponsLoading ? '加载中...' : '暂无可用优惠券'}
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
            <span className="text-accent">
              优惠券{selectedFace?.isPercentage ? `（${selectedFace.amountText}）` : ''}
            </span>
            <span className="text-accent">-¥{formatPrice(discountCents)}</span>
          </div>
        )}
        <div className="flex justify-between text-sm">
          <span className="text-text-secondary">运费</span>
          <span className="text-ink-black">免运费</span>
        </div>
        <div className="border-t border-warm-gray-200 pt-3 flex items-center justify-between">
          <span className="text-sm text-text-secondary">
            共 {selectedCount} 件
          </span>
          <div className="flex items-baseline gap-2">
            <span className="text-xs text-text-secondary">实付</span>
            <PriceDisplay cents={payCents} size="lg" />
          </div>
        </div>
        {submitError && (
          <p role="alert" className="text-xs text-danger">{submitError}</p>
        )}
        <Button
          onClick={handleSubmit}
          variant="gold"
          size="lg"
          className="w-full"
          loading={submitting}
          disabled={degraded}
        >
          提交订单
        </Button>
        {source === 'server' && (
          <p className="text-xs text-text-tertiary text-center">
            商品清单与勾选状态以服务端购物车为准
          </p>
        )}
      </GlassCard>
    </div>
  );
}

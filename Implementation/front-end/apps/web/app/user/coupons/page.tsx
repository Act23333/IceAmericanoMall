'use client';

import { useState } from 'react';
import { useAvailableCoupons, useUsedCoupons, useCouponTemplates, useClaimCoupon } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';
import { formatCouponFace } from '@icedmall/utils';

/**
 * `user_coupon.status` 口径 —— 后端 `UserCouponStatusEnum`（**1=未使用 / 2=已使用 / 3=已过期**）。
 * ⚠️ 曾经写作 `0=未使用 1=已使用 2=已过期`（错口径，整表错位一格），已按后端修正。
 */
const COUPON_STATUS_TEXT: Record<number, string> = {
  1: '可用',
  2: '已使用',
  3: '已过期',
};

export default function CouponsPage() {
  const [tab, setTab] = useState<'mine' | 'center'>('center');
  // 券面 `name` / `value` / `min_amount` / `discount_type` 已随 `UserCouponVO` 一并返回
  // （后端本地 join 回填 + fail-open）；面额渲染一律经 `formatCouponFace()`（按 discount_type 解释 value 单位）
  const { data: available, isLoading: availableLoading } = useAvailableCoupons();
  const { data: used } = useUsedCoupons();
  const { data: templates } = useCouponTemplates();
  const claim = useClaimCoupon();
  const [msg, setMsg] = useState('');

  // 我的券 = 未使用（M3）+ 已使用/已过期（M4），两者同一 VO 形状
  const myCoupons = [...(available ?? []), ...(used ?? [])];

  return (
    <div className="mx-auto max-w-2xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-6">优惠券</h1>
      <div className="flex rounded-xl bg-warm-100 p-1 mb-6">
        {(['center', 'mine'] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)}
            className={`flex-1 py-2 text-sm rounded-lg transition-all ${
              tab === t ? 'bg-white text-ink-black shadow-sm' : 'text-warm-600'
            }`}>{t === 'center' ? '领券中心' : '我的优惠券'}</button>
        ))}
      </div>
      {msg && <p className="text-sm text-center mb-3 text-accent-green">{msg}</p>}

      {tab === 'center' && (
        <div className="space-y-3">
          {templates?.map((c) => {
            // 券模板面额：同样按 `discount_type` 解释 `value` 单位（1/3=分 → ¥30.00；2=百分比 → 8.5折）
            // ⚠️ 不用已 DEPRECATED 的 `coupon.type`；`price_in_cents` 是付费券售价，不是面额
            const face = formatCouponFace({
              discountType: c.discount_type,
              value: c.value,
              minAmount: c.min_amount,
            });
            return (
              <GlassCard key={c.coupon_id} className="p-4 flex items-center justify-between" blur="sm">
                <div>
                  <p className="text-sm font-medium text-ink-black">{c.name}</p>
                  <p className="text-xs text-warm-400 mt-1">
                    {face.amountText}
                    {face.thresholdText !== '无门槛' && <> · {face.thresholdText}</>}
                  </p>
                </div>
                <Button variant="secondary" size="sm" loading={claim.isPending}
                  onClick={() => claim.mutate(c.coupon_id, { onSuccess: () => setMsg('领取成功!'), onError: (e: any) => setMsg(e?.message ?? '领取失败') })}>
                  立即领取
                </Button>
              </GlassCard>
            );
          })}
          {(!templates || templates.length === 0) && <p className="text-center text-sm text-warm-400 py-10">暂无可用优惠券</p>}
        </div>
      )}

      {tab === 'mine' && (
        <div className="space-y-3">
          {myCoupons.map((uc) => {
            // status: 1=未使用（可用）2=已使用 3=已过期 —— 非「未使用」一律渲染失效样式
            const usable = uc.status === 1;
            // 券面（可用 / 已使用 / 已过期同一 VO 形状）按 `discount_type` 渲染；
            // 未知类型 → 「优惠券」，**绝不按满减口径渲染**（折扣券曾被渲染成 -¥0.85）
            const face = formatCouponFace({
              discountType: uc.discount_type,
              value: uc.value,
              minAmount: uc.min_amount,
            });
            return (
              <GlassCard key={uc.id} className="p-4 flex items-center justify-between gap-3" blur="sm">
                <div className="min-w-0">
                  {/* 券面 name 直接来自本响应；模板已删除（fail-open → null）时才回落面额兜底文案 */}
                  <p className={`text-sm font-medium truncate ${usable ? 'text-ink-black' : 'text-warm-400'}`}>
                    {uc.name ?? face.fallbackName}
                  </p>
                  <p className="text-xs text-warm-400 mt-1">
                    <span className={usable ? 'text-accent' : undefined}>{face.amountText}</span>
                    {' · '}
                    {face.thresholdText}
                  </p>
                </div>
                <span className={`shrink-0 text-xs px-2 py-0.5 rounded-full ${
                  usable ? 'bg-accent-green/10 text-accent-green' : 'bg-warm-100 text-warm-400'
                }`}>
                  {COUPON_STATUS_TEXT[uc.status] ?? `状态 ${uc.status}`}
                </span>
              </GlassCard>
            );
          })}
          {availableLoading && <p className="text-center text-sm text-warm-400 py-10">加载中…</p>}
          {!availableLoading && myCoupons.length === 0 && (
            <p className="text-center text-sm text-warm-400 py-10">暂无优惠券</p>
          )}
        </div>
      )}
    </div>
  );
}

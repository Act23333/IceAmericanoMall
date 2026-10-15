'use client';

import { useState } from 'react';
import { useAvailableCoupons, useUsedCoupons, useCouponTemplates, useClaimCoupon } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';

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
  // 券面 `name` / `value` / `min_amount` 已随 `UserCouponVO` 一并返回（后端本地 join 回填）
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
          {templates?.map((c) => (
            <GlassCard key={c.coupon_id} className="p-4 flex items-center justify-between" blur="sm">
              <div>
                <p className="text-sm font-medium text-ink-black">{c.name}</p>
                <p className="text-xs text-warm-400 mt-1">
                  {c.type === 1 ? `满减 ¥${formatPrice(c.value)}` : `${c.value}折`}
                  {c.min_amount > 0 && <> · 满¥{formatPrice(c.min_amount)}可用</>}
                </p>
              </div>
              <Button variant="secondary" size="sm" loading={claim.isPending}
                onClick={() => claim.mutate(c.coupon_id, { onSuccess: () => setMsg('领取成功!'), onError: (e: any) => setMsg(e?.message ?? '领取失败') })}>
                立即领取
              </Button>
            </GlassCard>
          ))}
          {(!templates || templates.length === 0) && <p className="text-center text-sm text-warm-400 py-10">暂无可用优惠券</p>}
        </div>
      )}

      {tab === 'mine' && (
        <div className="space-y-3">
          {myCoupons.map((uc) => {
            // status: 1=未使用（可用）2=已使用 3=已过期 —— 非「未使用」一律渲染失效样式
            const usable = uc.status === 1;
            return (
              <GlassCard key={uc.id} className="p-4 flex items-center justify-between gap-3" blur="sm">
                <div className="min-w-0">
                  {/* 券面 name 直接来自本响应；模板已删除（fail-open → null）时才回落占位文案 */}
                  <p className={`text-sm font-medium truncate ${usable ? 'text-ink-black' : 'text-warm-400'}`}>
                    {uc.name ?? '优惠券'}
                  </p>
                  <p className="text-xs text-warm-400 mt-1">
                    {uc.min_amount != null && uc.min_amount > 0
                      ? `满¥${formatPrice(uc.min_amount)}可用`
                      : '无门槛'}
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

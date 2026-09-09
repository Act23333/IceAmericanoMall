'use client';

import { useState } from 'react';
import { useAvailableCoupons, useCouponTemplates, useClaimCoupon } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';

export default function CouponsPage() {
  const [tab, setTab] = useState<'mine' | 'center'>('center');
  const { data: available } = useAvailableCoupons();
  const { data: templates } = useCouponTemplates();
  const claim = useClaimCoupon();
  const [msg, setMsg] = useState('');

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
            <GlassCard key={c.id} className="p-4 flex items-center justify-between" blur="sm">
              <div>
                <p className="text-sm font-medium text-ink-black">{c.name}</p>
                <p className="text-xs text-warm-400 mt-1">
                  {c.type === 1 ? `满减 ¥${formatPrice(c.value)}` : `${c.value}折`}
                  {c.minAmount > 0 && <> · 满¥{formatPrice(c.minAmount)}可用</>}
                </p>
              </div>
              <Button variant="secondary" size="sm" loading={claim.isPending}
                onClick={() => claim.mutate(c.couponId!, { onSuccess: () => setMsg('领取成功!'), onError: (e: any) => setMsg(e?.message ?? '领取失败') })}>
                立即领取
              </Button>
            </GlassCard>
          ))}
          {(!templates || templates.length === 0) && <p className="text-center text-sm text-warm-400 py-10">暂无可用优惠券</p>}
        </div>
      )}

      {tab === 'mine' && (
        <div className="space-y-3">
          {available?.map((uc) => (
            <GlassCard key={uc.id} className="p-4" blur="sm">
              <p className="text-sm font-medium text-ink-black">优惠券 #{uc.couponId}</p>
              <p className="text-xs text-warm-400 mt-1">状态: {uc.status === 1 ? '可用' : uc.status === 2 ? '已使用' : '已过期'}</p>
            </GlassCard>
          ))}
          {(!available || available.length === 0) && <p className="text-center text-sm text-warm-400 py-10">暂无优惠券</p>}
        </div>
      )}
    </div>
  );
}

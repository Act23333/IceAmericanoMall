'use client';

import { useState } from 'react';
import { useCouponTemplates, useClaimCoupon, useGrabCoupon } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import { Ticket, Users, Clock, Zap } from 'lucide-react';
import Link from 'next/link';

/**
 * 领券中心 — V5.0
 *
 * 展示所有可领取的优惠券模板:
 *   - 普通券: 直接领取
 *   - 抢券: 需要秒杀抢 (grabType=NEED_GRAB)
 */
export default function CouponsPage() {
  const { data: templates, isLoading } = useCouponTemplates();
  const claim = useClaimCoupon();
  const grab = useGrabCoupon();
  const [claimed, setClaimed] = useState<Set<string>>(new Set());

  const handleClaim = async (couponId: string, isGrab: boolean) => {
    try {
      if (isGrab) {
        await grab.mutateAsync(couponId);
      } else {
        await claim.mutateAsync(couponId);
      }
      setClaimed((prev) => new Set([...prev, couponId]));
    } catch (e: any) {
      alert(e?.message ?? '领取失败');
    }
  };

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-4 pt-24 pb-20">
        <div className="h-8 w-40 rounded bg-warm-gray-100 animate-pulse mb-8" />
        <div className="grid gap-4 md:grid-cols-2">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-32 rounded-2xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (!templates || templates.length === 0) {
    return (
      <div className="mx-auto max-w-4xl px-4 pt-24 pb-20 text-center">
        <div className="text-6xl select-none">🎫</div>
        <h2 className="mt-4 text-lg font-medium text-ink-black">暂无可用优惠券</h2>
        <p className="mt-2 text-sm text-text-secondary">敬请期待更多优惠活动</p>
        <Link href="/marketplace" className="mt-6 inline-block text-sm text-accent hover:underline">
          去商城逛逛 →
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 pt-24 pb-20">
      <div className="flex items-center gap-3 mb-8">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gold-light">
          <Ticket className="h-5 w-5 text-gold" />
        </div>
        <div>
          <h1 className="text-2xl font-light text-ink-black">领券中心</h1>
          <p className="text-xs text-text-secondary mt-0.5">领取优惠券，下单更优惠</p>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {templates.map((tpl: any) => {
          const cid = tpl.couponId || String(tpl.id);
          const isClaimed = claimed.has(cid);
          const isGrab = (tpl.grabType === 'NEED_GRAB' || tpl.grabType === 2);
          const remaining = tpl.totalStock > 0 ? Math.max(0, tpl.totalStock - (tpl.claimedCount || 0)) : Infinity;
          const isSoldOut = tpl.totalStock > 0 && remaining <= 0;

          return (
            <GlassCard key={cid} className="flex overflow-hidden" blur="sm">
              {/* 左侧金额区 */}
              <div className="flex w-28 shrink-0 flex-col items-center justify-center bg-accent-light py-4">
                <span className="text-3xl font-bold text-accent price">
                  {tpl.discountType === 1 /* FIXED */ ? '¥' : ''}
                  {tpl.priceInCents ? formatPrice(tpl.priceInCents) : formatPrice(tpl.value || 0)}
                  {tpl.discountType === 2 /* PERCENTAGE */ ? '折' : ''}
                </span>
                <span className="text-xs text-text-secondary mt-1">
                  {tpl.minAmountInCents > 0
                    ? `满¥${formatPrice(tpl.minAmountInCents)}可用`
                    : tpl.minAmount > 0
                    ? `满¥${formatPrice(tpl.minAmount)}可用`
                    : '无门槛'}
                </span>
              </div>

              {/* 右侧信息区 */}
              <div className="flex-1 p-4 flex flex-col justify-between">
                <div>
                  <h3 className="text-sm font-medium text-ink-black">{tpl.name}</h3>
                  <div className="flex items-center gap-3 mt-1.5 text-xs text-text-tertiary">
                    <span className="flex items-center gap-1">
                      <Users className="h-3 w-3" />
                      {tpl.couponCategory === 1 || tpl.couponCategory === 'PLATFORM' ? '平台券' : '店铺券'}
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="h-3 w-3" />
                      {tpl.endTime ? new Date(tpl.endTime).toLocaleDateString() : '有效期见详情'}
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between mt-2">
                  {tpl.totalStock > 0 && (
                    <div className="flex-1 mr-3">
                      <div className="flex justify-between text-xs text-text-tertiary mb-0.5">
                        <span>已领 {Math.round(((tpl.claimedCount || 0) / tpl.totalStock) * 100)}%</span>
                        <span>剩 {remaining} 张</span>
                      </div>
                      <div className="h-1 rounded-full bg-warm-gray-200 overflow-hidden">
                        <div
                          className="h-full rounded-full bg-accent transition-all duration-300"
                          style={{ width: `${Math.min(100, ((tpl.claimedCount || 0) / tpl.totalStock) * 100)}%` }}
                        />
                      </div>
                    </div>
                  )}
                  <Button
                    variant={isGrab ? 'gold' : 'primary'}
                    size="sm"
                    disabled={isClaimed || isSoldOut}
                    loading={(claim.isPending || grab.isPending)}
                    onClick={() => handleClaim(cid, isGrab)}
                  >
                    {isGrab && <Zap className="h-3.5 w-3.5 mr-1" />}
                    {isSoldOut ? '已抢光' : isClaimed ? '已领取' : isGrab ? '抢券' : '立即领取'}
                  </Button>
                </div>
              </div>
            </GlassCard>
          );
        })}
      </div>
    </div>
  );
}

'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { apiClient, type FlashSaleEntity } from '@icedmall/api';
import { formatPrice } from '@icedmall/utils';
import { Zap } from 'lucide-react';

/**
 * 秒杀横幅 — 商城首页快速入口
 *
 * 设计系统品牌色适配 (不使用京东红)
 * 使用品牌 amber/gold 作为秒杀色
 */
export function FlashSaleBanner() {
  const { data } = useQuery<FlashSaleEntity[]>({
    queryKey: ['flash-sales', 'active-banner'],
    queryFn: () => apiClient<FlashSaleEntity[]>('/api/flash'),
    staleTime: 30_000,
  });

  if (!data || data.length === 0) return null;

  return (
    <section className="py-6">
      <div className="rounded-2xl bg-gradient-to-r from-amber/5 via-gold-light/30 to-amber/5 border border-amber/20 p-5">
        <div className="flex items-center justify-between mb-4">
          <h2 className="flex items-center gap-2 text-base font-medium text-ink-black">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber/10">
              <Zap className="h-4 w-4 text-amber" />
            </span>
            限时秒杀
          </h2>
          <Link href="/marketplace/flash" className="text-xs text-text-secondary hover:text-amber transition-colors">
            查看全部 →
          </Link>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {data.slice(0, 4).map((fs: any) => (
            <Link
              key={fs.id}
              href={`/product/${fs.productId || fs.id}`}
              className="group rounded-xl bg-white/70 backdrop-blur-sm border border-white/40 p-3 hover:shadow-card-hover hover:-translate-y-0.5 transition-all duration-300"
            >
              <p className="text-xl font-bold text-amber price">
                ¥{formatPrice(fs.flashPrice ?? fs.price ?? 0)}
              </p>
              {fs.flashPrice && fs.price && fs.flashPrice < fs.price && (
                <p className="text-xs text-text-tertiary line-through mt-0.5">
                  ¥{formatPrice(fs.price)}
                </p>
              )}
              <div className="mt-2 h-1.5 rounded-full bg-warm-gray-200 overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-amber to-amber/60 transition-all duration-500"
                  style={{
                    width: `${Math.min(100, ((fs.soldCount || 0) / Math.max(1, (fs.stock || 1) + (fs.soldCount || 0))) * 100)}%`,
                  }}
                />
              </div>
              <p className="text-xs text-text-tertiary mt-1">
                已抢 {((fs.soldCount || 0) / Math.max(1, (fs.stock || 1) + (fs.soldCount || 0)) * 100).toFixed(0)}%
              </p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

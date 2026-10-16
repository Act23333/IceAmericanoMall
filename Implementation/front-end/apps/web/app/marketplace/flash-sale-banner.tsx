'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { apiClient, type FlashSaleVO } from '@icedmall/api';
import { formatPrice } from '@icedmall/utils';
import { Zap } from 'lucide-react';

/**
 * 秒杀横幅 — 商城首页快速入口
 *
 * 设计系统品牌色适配 (不使用京东红)
 * 使用品牌 amber/gold 作为秒杀色
 *
 * 进度口径：**直接用后端 `progress_percent`**（= sold_count / 秒杀总库存 stock，
 * 整数向下取整）——不再前端自算 `sold/(stock+sold)`（分母口径与契约不符）。
 * 商品字段 `spu_name` / `main_image` / `original_price` 由后端读时回填（fail-open → null）。
 *
 * ⚠️ B3 字段收口（ADR-061）：`product_id` → `spu_id`、`product_name` → `spu_name`、
 *   `product_image` → `main_image`。秒杀卡**不做商品页深链**（SPU 业务键无法寻址仍收技术键的
 *   `/product/{id}`，契约 §十二 12.2 R1）；入口保留「查看全部」→ `/marketplace/flash`。
 */
export function FlashSaleBanner() {
  const { data } = useQuery<FlashSaleVO[]>({
    queryKey: ['flash-sales', 'active-banner'],
    queryFn: () => apiClient<FlashSaleVO[]>('/api/flash'),
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
          {data.slice(0, 4).map((fs) => {
            // 后端已给已售进度（分母 = 秒杀总库存）；null（老数据/降级）按 0 展示
            const progress = fs.progress_percent ?? 0;
            return (
              <div
                key={fs.id}
                className="group rounded-xl bg-white/70 backdrop-blur-sm border border-white/40 p-3"
              >
                <div className="mb-2 h-20 w-full overflow-hidden rounded-lg bg-warm-100">
                  {fs.main_image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={fs.main_image}
                      alt={fs.spu_name ?? ''}
                      className="h-full w-full object-cover"
                      loading="lazy"
                      decoding="async"
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-2xl select-none">⚡</div>
                  )}
                </div>
                {/* `spu_name` 回填缺失时回落中性文案（不显示任何 id） */}
                <p className="truncate text-sm text-ink-black">{fs.spu_name ?? '限时秒杀商品'}</p>
                <div className="mt-0.5 flex items-baseline gap-1.5">
                  <span className="text-xl font-bold text-amber price">
                    ¥{formatPrice(fs.flash_price ?? 0)}
                  </span>
                  {fs.original_price != null && fs.original_price > (fs.flash_price ?? 0) && (
                    <span className="text-xs text-text-tertiary line-through">
                      ¥{formatPrice(fs.original_price)}
                    </span>
                  )}
                </div>
                <div className="mt-2 h-1.5 rounded-full bg-warm-gray-200 overflow-hidden">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-amber to-amber/60 transition-all duration-500"
                    style={{ width: `${Math.min(100, progress)}%` }}
                  />
                </div>
                <p className="text-xs text-text-tertiary mt-1">已抢 {progress}%</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

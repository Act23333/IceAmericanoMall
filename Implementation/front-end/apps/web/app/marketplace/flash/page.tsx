'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useFlashSales, useFlashBuy } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import { Zap, Clock, ShoppingCart } from 'lucide-react';
import Link from 'next/link';

/**
 * 秒杀频道 — V5.0
 *
 * 京东标准秒杀页:
 *   - 进行中的秒杀场次
 *   - 毫秒级倒计时
 *   - 库存进度条
 *   - 立即抢购 (需要地址)
 */
export default function FlashSalePage() {
  const router = useRouter();
  const { data: flashSales, isLoading } = useFlashSales();
  const flashBuy = useFlashBuy();
  const [now, setNow] = useState(Date.now());

  // 每秒更新倒计时
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const handleBuy = useCallback(
    async (flashId: number) => {
      const addressId = prompt('请输入收货地址ID (演示: 输入数字ID)');
      if (!addressId) return;
      try {
        const result = await flashBuy.mutateAsync({ flashId, addressId: Number(addressId) } as any);
        if ((result as any)?.orderNo) {
          router.push(`/shop/pay/${(result as any).orderNo}`);
        }
      } catch (e: any) {
        alert(e?.message ?? '抢购失败');
      }
    },
    [flashBuy, router]
  );

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-4 pt-24 pb-20">
        <div className="h-8 w-48 rounded bg-warm-gray-100 animate-pulse mb-8" />
        <div className="grid gap-4 md:grid-cols-2">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-48 rounded-2xl bg-warm-gray-100 animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (!flashSales || flashSales.length === 0) {
    return (
      <div className="mx-auto max-w-4xl px-4 pt-24 pb-20 text-center">
        <div className="text-6xl select-none">⚡</div>
        <h2 className="mt-4 text-lg font-medium text-ink-black">暂无秒杀活动</h2>
        <p className="mt-2 text-sm text-text-secondary">敬请期待下一场秒杀</p>
        <Link href="/marketplace" className="mt-6 inline-block text-sm text-accent hover:underline">
          去商城逛逛 →
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 pt-24 pb-20">
      {/* 头部 */}
      <div className="flex items-center gap-3 mb-8">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber/10">
          <Zap className="h-5 w-5 text-amber" />
        </div>
        <div>
          <h1 className="text-2xl font-light text-ink-black">限时秒杀</h1>
          <p className="text-xs text-text-secondary mt-0.5">手慢无，立即抢购</p>
        </div>
      </div>

      {/* 秒杀商品列表 */}
      <div className="grid gap-4 md:grid-cols-2">
        {flashSales.map((fs: any) => {
          const startMs = new Date(fs.startTime).getTime();
          const endMs = new Date(fs.endTime).getTime();
          const isUpcoming = now < startMs;
          const isEnded = now > endMs;
          const remaining = isUpcoming ? startMs - now : endMs - now;
          // 库存进度
          const stockTotal = (fs.stock || 0) + (fs.soldCount || 0);
          const stockProgress = stockTotal > 0 ? Math.round(((fs.soldCount || 0) / stockTotal) * 100) : 0;

          const h = Math.floor(remaining / 3600000);
          const m = Math.floor((remaining % 3600000) / 60000);
          const s = Math.floor((remaining % 60000) / 1000);

          return (
            <GlassCard key={fs.id} className="overflow-hidden" blur="md" hover>
              {/* 倒计时栏 */}
              <div className="flex items-center justify-between px-4 py-2.5 bg-warm-gray-100/50 border-b border-warm-gray-200/50">
                <span className="flex items-center gap-1.5 text-xs font-medium text-amber">
                  <Clock className="h-3.5 w-3.5" />
                  {isUpcoming ? '即将开始' : isEnded ? '已结束' : '距结束'}
                </span>
                {!isEnded && (
                  <span className="font-mono text-sm font-semibold text-ink-black tabular-nums">
                    {String(h).padStart(2, '0')}:{String(m).padStart(2, '0')}:{String(s).padStart(2, '0')}
                  </span>
                )}
                {isEnded && <span className="text-xs text-text-tertiary">已结束</span>}
              </div>

              {/* 商品信息 */}
              <div className="flex gap-4 p-4">
                <div className="h-24 w-24 shrink-0 overflow-hidden rounded-xl bg-warm-gray-100">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={fs.mainImage || fs.productImage || '/placeholder.png'}
                    alt={fs.productName || '秒杀商品'}
                    className="h-full w-full object-cover"
                    loading="lazy"
                  />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-medium text-ink-black line-clamp-2">
                    {fs.productName || '秒杀商品'}
                  </h3>
                  <div className="mt-1.5 flex items-baseline gap-2">
                    <span className="text-lg font-bold text-amber price">
                      ¥{formatPrice(fs.flashPrice ?? fs.price ?? 0)}
                    </span>
                    {fs.price && fs.flashPrice && fs.flashPrice < fs.price && (
                      <span className="text-xs text-text-tertiary line-through">
                        ¥{formatPrice(fs.price)}
                      </span>
                    )}
                  </div>

                  {/* 库存进度 */}
                  <div className="mt-2">
                    <div className="flex justify-between text-xs text-text-tertiary mb-0.5">
                      <span>已抢 {stockProgress}%</span>
                      <span>剩余 {fs.stock ?? 0} 件</span>
                    </div>
                    <div className="h-1.5 rounded-full bg-warm-gray-200 overflow-hidden">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-amber to-amber/60 transition-all duration-500"
                        style={{ width: `${stockProgress}%` }}
                      />
                    </div>
                  </div>

                  {/* 按钮 */}
                  <Button
                    variant={isEnded ? 'ghost' : 'primary'}
                    size="sm"
                    className="mt-3 w-full"
                    disabled={isEnded || isUpcoming || (fs.stock ?? 0) <= 0}
                    loading={flashBuy.isPending}
                    onClick={() => handleBuy(fs.id)}
                  >
                    <ShoppingCart className="h-3.5 w-3.5 mr-1" />
                    {isEnded ? '已结束' : isUpcoming ? '即将开始' : (fs.stock ?? 0) <= 0 ? '已售罄' : '立即抢购'}
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

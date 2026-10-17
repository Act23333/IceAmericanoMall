'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useFlashSales, useFlashBuy } from '@icedmall/api';
import { Button, GlassCard } from '@icedmall/ui';
import { formatPrice, spuHref } from '@icedmall/utils';
import { Zap, Clock, ShoppingCart } from 'lucide-react';
import Link from 'next/link';

/**
 * 秒杀频道 — V5.0（2026-10-17 B3：字段按 ADR-061 收口）
 *
 * 京东标准秒杀页:
 *   - 进行中的秒杀场次
 *   - 毫秒级倒计时
 *   - 已售进度（**后端 `progress_percent`**，分母 = 秒杀总库存 `stock`）
 *   - 真实商品（`spu_name` / `main_image` / `original_price`，后端读时回填 fail-open）
 *   - 立即抢购 (需要地址)
 *
 * ⚠️ 字段改名（B3）：`product_id` → **`spu_id`**（SPU 业务键 32-hex）、
 *   `product_name` → **`spu_name`**、`product_image` → **`main_image`**
 *   （主图名 B2 裁决统一为 `main_image`，`spu_image` / `product_image` 均已废弃）。
 *
 * ✅ 秒杀卡回链商品页（2026-10-18 B8 **恢复**）：P2 详情路径变量已改为 **SPU 业务键**
 *   （契约 §P2、ADR-062 §决策 3）⇒ `spu_id` 可直接跳 `/spu/{spu_id}`。
 *   B3 期「业务键无法寻址 ⇒ 不做深链」的限制已解除。缺 `spu_id`（回填 fail-open）时
 *   `spuHref()` 返回 null ⇒ 图/标题不可点（不渲染空链接）；抢购按钮始终按 `flash_id` 寻址，
 *   与商品页链接互不影响。
 */
export default function FlashSalePage() {
  const router = useRouter();
  const { data: flashSales, isLoading } = useFlashSales();
  const flashBuy = useFlashBuy();
  const [now, setNow] = useState(Date.now());
  const [buyError, setBuyError] = useState<string | null>(null);

  // 每秒更新倒计时
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const handleBuy = useCallback(
    async (flashId: number) => {
      setBuyError(null);
      const addressInput = prompt('请输入收货地址ID (演示: 输入数字ID)');
      if (!addressInput) return;
      /*
        数值转换定性（2026-10 B10 第 7 项）：`address_id` 在后端是技术主键 `Long`
        （`address.id`，非 32-hex 业务键），故此处 `Number()` 合法；
        但旧实现没有防护 —— 非数字输入会得 `NaN`，`JSON.stringify` 把 `NaN` 变成 `null`
        再发给后端，报错信息对用户毫无意义。故先校验，非法就地提示、不发请求。
      */
      const addressId = Number(addressInput);
      if (!Number.isFinite(addressId) || addressId <= 0) {
        setBuyError('收货地址 ID 必须是正整数');
        return;
      }
      try {
        // 响应 `FlashBuyVO` = { success, order_no, flash_id, flash_price, spu_id, remaining_stock }
        // （B3：`product_id` → `spu_id`）
        const result = await flashBuy.mutateAsync({ flashId, addressId });
        if (result?.order_no) {
          router.push(`/shop/pay/${result.order_no}`);
        }
      } catch (e: unknown) {
        setBuyError(e instanceof Error ? e.message : '抢购失败');
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
      <div className="flex items-center gap-3 mb-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber/10">
          <Zap className="h-5 w-5 text-amber" />
        </div>
        <div>
          <h1 className="text-2xl font-light text-ink-black">限时秒杀</h1>
          <p className="text-xs text-text-secondary mt-0.5">手慢无，立即抢购</p>
        </div>
      </div>

      {/* 抢购失败态（不吞错误） */}
      {buyError && (
        <p role="alert" className="mb-4 rounded-lg bg-danger/5 px-3 py-2 text-xs text-danger">
          抢购失败：{buyError}
        </p>
      )}

      {/* 秒杀商品列表 */}
      <div className="grid gap-4 md:grid-cols-2">
        {flashSales.map((fs) => {
          const startMs = new Date(fs.start_time).getTime();
          const endMs = new Date(fs.end_time).getTime();
          const isUpcoming = now < startMs;
          const isEnded = now > endMs;
          const remaining = isUpcoming ? startMs - now : endMs - now;
          // 已售进度：直接用后端 progress_percent（分母 = 秒杀**总**库存 stock，整数向下取整）
          const stockProgress = fs.progress_percent ?? 0;
          // 剩余量 = 总库存 − 已售（后端 FlashSale.remainingStock() 同口径；stock 是总量而非余量）
          const remainingStock = Math.max(0, (fs.stock ?? 0) - (fs.sold_count ?? 0));
          // 回链 `/spu/{spu_id}`（B8 恢复；缺业务键 → null ⇒ 图/标题不渲染链接）
          const spuLink = spuHref(fs.spu_id);
          const imageBlock = (
            <div className="h-24 w-24 shrink-0 overflow-hidden rounded-xl bg-warm-gray-100">
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
                <div className="flex h-full w-full items-center justify-center text-3xl select-none">⚡</div>
              )}
            </div>
          );

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
                {spuLink ? (
                  <Link href={spuLink} aria-label={`查看 ${fs.spu_name ?? '秒杀商品'} 详情`}>
                    {imageBlock}
                  </Link>
                ) : (
                  imageBlock
                )}
                <div className="flex-1 min-w-0">
                  {/* `spu_name` 由后端读时回填；缺失（SKU 已删除/降级）时回落中性文案 —— 不显示任何 id */}
                  {spuLink ? (
                    <Link href={spuLink} className="text-sm font-medium text-ink-black line-clamp-2 hover:underline">
                      {fs.spu_name ?? '秒杀商品'}
                    </Link>
                  ) : (
                    <h3 className="text-sm font-medium text-ink-black line-clamp-2">
                      {fs.spu_name ?? '秒杀商品'}
                    </h3>
                  )}
                  <div className="mt-1.5 flex items-baseline gap-2">
                    <span className="text-lg font-bold text-amber price">
                      ¥{formatPrice(fs.flash_price ?? 0)}
                    </span>
                    {fs.original_price != null && fs.original_price > (fs.flash_price ?? 0) && (
                      <span className="text-xs text-text-tertiary line-through">
                        ¥{formatPrice(fs.original_price)}
                      </span>
                    )}
                  </div>

                  {/* 已售进度（后端 progress_percent）+ 剩余件数 */}
                  <div className="mt-2">
                    <div className="flex justify-between text-xs text-text-tertiary mb-0.5">
                      <span>已抢 {stockProgress}%</span>
                      <span>剩余 {remainingStock} 件</span>
                    </div>
                    <div className="h-1.5 rounded-full bg-warm-gray-200 overflow-hidden">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-amber to-amber/60 transition-all duration-500"
                        style={{ width: `${Math.min(100, stockProgress)}%` }}
                      />
                    </div>
                  </div>

                  {/* 按钮 */}
                  <Button
                    variant={isEnded ? 'ghost' : 'primary'}
                    size="sm"
                    className="mt-3 w-full"
                    disabled={isEnded || isUpcoming || remainingStock <= 0}
                    loading={flashBuy.isPending}
                    onClick={() => handleBuy(fs.id)}
                  >
                    <ShoppingCart className="h-3.5 w-3.5 mr-1" />
                    {isEnded ? '已结束' : isUpcoming ? '即将开始' : remainingStock <= 0 ? '已售罄' : '立即抢购'}
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

'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient, type ShopVO } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { Store, Users } from 'lucide-react';
import { LoadFailedState } from '@/app/components/load-failed-state';

/**
 * 商品详情页 — 店铺信息卡片
 *
 * 后端 `ShopVO` = { shop_id, seller_id, shop_name, shop_logo, contact_phone, address,
 *                  follower_count, status }（`GET /api/shop/{sellerId}` 走 `getVisibleShop`
 *                  → `enrichFollowerCounts`，故单店查询同样带回关注数）。
 *
 * ⚠️ `follower_count` **null ≠ 0**（契约 §4.3.1 / ADR-057）：
 *    null = 未知（Redis 不可用 → fail-open）；0 = 确实无人关注。
 *    故仅在非 null 时渲染——**不得**回落为 0 展示。
 *
 * ── 2026-10 B10：移除「进店逛逛」死链 ──────────────────────────────────────
 * 旧实现 `<Link href={`/shop/${sellerId}`}>` 指向的 `app/shop/[sellerId]` **页面不存在**
 * ⇒ 点击必 404。本批只删入口、**不建页面**：按 `sellerId` 建会做成旧形态，而店铺主页的
 * 新规范是 `SH1 GET /api/shops/{shopId}`（ADR-019，shop 与 seller 是 1:N）——
 * 需要时**按 shopId 新建**，见 `apps/web` 的 `shop/[shopId]` 待立项。
 * 删掉的只是入口：下方**店铺信息展示**与关注关系（`GET /api/shop/{sellerId}` +
 * `GET /api/shop/follow/count` 回填的 `follower_count`）全部保留、照常工作。
 *
 * ── 2026-10 B10：失败态 ─────────────────────────────────────────────────
 * 旧实现 `if (!shop) return null` ⇒ 请求失败时整张卡**静默消失**（看起来像"没有店铺信息"）。
 * 现在失败渲染区块级失败态（带重试），与「该商品无店铺」区分开。
 */

export function ShopInfoCard({ sellerId }: { sellerId: number }) {
  const { data: shop, isError, error, isFetching, refetch } = useQuery<ShopVO>({
    queryKey: ['shop', sellerId],
    queryFn: () => apiClient<ShopVO>(`/api/shop/${sellerId}`),
    staleTime: 5 * 60 * 1000,
  });

  if (isError) {
    return (
      <LoadFailedState
        compact
        title="店铺信息加载失败"
        description="没能获取到店铺信息（不是「该商品无店铺」）。"
        detail={error instanceof Error ? error.message : undefined}
        onRetry={() => { void refetch(); }}
        retrying={isFetching}
      />
    );
  }

  if (!shop) return null;

  return (
    <GlassCard className="p-4 space-y-3" blur="sm">
      <div className="flex items-center gap-3">
        {shop.shop_logo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={shop.shop_logo}
            alt={shop.shop_name}
            className="h-10 w-10 rounded-xl object-cover"
          />
        ) : (
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-light">
            <Store className="h-5 w-5 text-accent" />
          </div>
        )}
        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-medium text-ink-black truncate">{shop.shop_name}</h3>
          <div className="flex items-center gap-3 text-xs text-text-tertiary mt-0.5">
            {shop.address && <span className="truncate">{shop.address}</span>}
            {/* null = 未知 → 整块不渲染（0 才表示确实无人关注，照常显示 0） */}
            {shop.follower_count != null && (
              <span className="flex shrink-0 items-center gap-1">
                <Users className="h-3 w-3" />
                {shop.follower_count} 人关注
              </span>
            )}
          </div>
        </div>
      </div>
    </GlassCard>
  );
}

'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient, type ShopVO } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { Store, ChevronRight, Users } from 'lucide-react';
import Link from 'next/link';

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
 */
export function ShopInfoCard({ sellerId }: { sellerId: number }) {
  const { data: shop } = useQuery<ShopVO>({
    queryKey: ['shop', sellerId],
    queryFn: () => apiClient<ShopVO>(`/api/shop/${sellerId}`),
    staleTime: 5 * 60 * 1000,
  });

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
        <div className="flex items-center gap-2">
          <Link href={`/shop/${sellerId}`}>
            <Button variant="ghost" size="sm">
              进店逛逛
              <ChevronRight className="h-3.5 w-3.5 ml-0.5" />
            </Button>
          </Link>
        </div>
      </div>
    </GlassCard>
  );
}

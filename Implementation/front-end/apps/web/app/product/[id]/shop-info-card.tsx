'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@icedmall/api';
import { GlassCard, Button } from '@icedmall/ui';
import { Store, Star, Users, ChevronRight } from 'lucide-react';
import Link from 'next/link';

/**
 * 商品详情页 — 店铺信息卡片
 *
 * 京东标准: 商品详情页展示店铺名称/Logo/评分/关注数
 *   关注按钮 + 进店逛逛
 */
export function ShopInfoCard({ sellerId }: { sellerId: number }) {
  const { data: shop } = useQuery<any>({
    queryKey: ['shop', sellerId],
    queryFn: () => apiClient<any>(`/api/shop/${sellerId}`),
    staleTime: 5 * 60 * 1000,
  });

  if (!shop) return null;

  return (
    <GlassCard className="p-4 space-y-3" blur="sm">
      <div className="flex items-center gap-3">
        {shop.shopLogo ? (
          <img
            src={shop.shopLogo}
            alt={shop.shopName}
            className="h-10 w-10 rounded-xl object-cover"
          />
        ) : (
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-light">
            <Store className="h-5 w-5 text-accent" />
          </div>
        )}
        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-medium text-ink-black truncate">{shop.shopName}</h3>
          <div className="flex items-center gap-3 text-xs text-text-tertiary mt-0.5">
            {shop.rating && (
              <span className="flex items-center gap-0.5">
                <Star className="h-3 w-3 fill-amber text-amber" />
                {shop.rating}
              </span>
            )}
            {shop.followerCount !== undefined && (
              <span className="flex items-center gap-0.5">
                <Users className="h-3 w-3" />
                {shop.followerCount > 1000
                  ? `${(shop.followerCount / 1000).toFixed(1)}k`
                  : shop.followerCount}{' '}
                关注
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

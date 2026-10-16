'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type PageResult, type FavoriteVO } from '@icedmall/api';
import { GlassCard } from '@icedmall/ui';
import { formatPrice } from '@icedmall/utils';
import Link from 'next/link';

/**
 * `FavoriteVO` = { id, user_id, **spu_id**, **spu_name**, main_image, price, status, create_time }
 * （2026-10-17 B3：`product_id` / `product_name` 已随 ADR-061 改名为 `spu_id` / `spu_name`，
 *  且 `spu_id` 为 **SPU 业务键 32-hex `String`**。）
 *
 * 2026-10-15 后端补齐商品展示摘要（读时批量回填，fail-open → null）：
 *   - `spu_name` / `main_image` / `price` 直接渲染真实商品（不再「商品 #1001」）；
 *   - `price` 为**整数分** → 展示层 `/100`（`formatPrice`）；
 *   - `status`：1=上架 2=下架 3=删除 → 非上架态渲染失效样式；null（回填缺失）不判失效。
 *
 * ⚠️ **回链商品详情的限制（契约 §十二 12.2 R1 待裁决，非本批可解）**：
 * `FavoriteVO` 只带 **SPU 业务键**，而商品详情 `GET /api/item/product/{id}` 的路径变量
 * **仍是技术键 `Long`**（R1 未收口）。业务键无法寻址该路径 ⇒ 旧代码
 * `href={`/product/${fav.product_id}`}` 实际渲染为 `/product/undefined`（且改名为 `spu_id`
 * 后若照抄会得到 `/product/<32hex>` → 后端 400）。
 * 故本页**不再伪造商品页深链**，改为卡片内直接渲染（不跳转），并在交付报告登记 R1 依赖。
 * 不显示任何 id 占位符（业务键亦不当作商品名展示）。
 */
export default function FavoritesPage() {
  const [page] = useState(1);
  const qc = useQueryClient();

  const { data, isLoading } = useQuery<PageResult<FavoriteVO>>({
    queryKey: ['favorites', page],
    queryFn: () => apiClient<PageResult<FavoriteVO>>(`/api/user/favorite?page=${page}&size=12`),
  });

  // query 参数名 `spu_id` —— 后端 `@RequestParam(name = "spu_id") String spuId`
  // （平台决策：线上字段与查询参数一律 snake_case；形参 `spuId` 是内部标识符，保持 camelCase）。
  // ⚠️ 旧代码发 `product_id`：该参数为**必填**，缺失即 400 ⇒ 「取消收藏」100% 失败。
  // `spu_id` 形态为严格 32 位 hex（契约 §1.8）⇒ URL 安全，无需 encodeURIComponent。
  const removeMutation = useMutation({
    mutationFn: (spuId: string) =>
      apiClient(`/api/user/favorite?spu_id=${spuId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['favorites'] }),
  });

  return (
    <div className="mx-auto max-w-6xl px-4 pt-24 pb-20">
      <h1 className="text-2xl font-light text-ink-black mb-6">我的收藏</h1>

      {removeMutation.isError && (
        <p role="alert" className="mb-4 text-xs text-danger">
          取消收藏失败：{(removeMutation.error as Error)?.message}
        </p>
      )}

      {isLoading ? (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[1,2,3,4].map(i => <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />)}
        </div>
      ) : (data?.records?.length ?? 0) === 0 ? (
        <div className="text-center py-20"><div className="text-6xl">⭐</div><p className="mt-4 text-warm-600">暂无收藏</p><Link href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">去逛逛</Link></div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {data?.records?.map((fav) => {
            // 1=上架 2=下架 3=删除；null = 回填缺失（fail-open）→ 不判失效，照常展示
            const invalid = fav.status === 2 || fav.status === 3;
            const invalidText = fav.status === 3 ? '商品已删除' : '商品已下架';
            return (
              <GlassCard key={fav.id} className="relative group p-3 text-center" blur="sm">
                <div className="block">
                  <div className="relative aspect-square overflow-hidden rounded-xl bg-warm-100">
                    {fav.main_image ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={fav.main_image}
                        alt={fav.spu_name ?? ''}
                        className={`h-full w-full object-cover transition-transform duration-300 group-hover:scale-105 ${
                          invalid ? 'opacity-50 grayscale' : ''
                        }`}
                        loading="lazy"
                        decoding="async"
                      />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center text-4xl">📦</div>
                    )}
                    {invalid && (
                      <span className="absolute inset-x-0 bottom-0 bg-ink-black/60 py-1 text-xs text-white">
                        {invalidText}
                      </span>
                    )}
                  </div>
                  {/* `spu_name` 为 null（商品已删除 / 读模型降级）时回落中性文案 —— 不回退显示任何 id */}
                  <p className={`mt-3 text-sm truncate ${invalid ? 'text-warm-400' : 'text-ink-black'}`}>
                    {fav.spu_name ?? '商品信息暂不可用'}
                  </p>
                  <p className={`mt-1 text-sm font-medium price ${invalid ? 'text-warm-400 line-through' : 'text-ink-black'}`}>
                    {fav.price != null ? `¥${formatPrice(fav.price)}` : '价格待更新'}
                  </p>
                </div>
                <button
                  onClick={(e) => { e.stopPropagation(); removeMutation.mutate(fav.spu_id); }}
                  disabled={removeMutation.isPending}
                  className="absolute top-2 right-2 p-1.5 rounded-full bg-white/80 text-danger opacity-0 group-hover:opacity-100 transition-opacity text-xs disabled:opacity-40"
                  title="取消收藏"
                  aria-label={`取消收藏 ${fav.spu_name ?? ''}`}
                >
                  ✕
                </button>
              </GlassCard>
            );
          })}
        </div>
      )}
    </div>
  );
}

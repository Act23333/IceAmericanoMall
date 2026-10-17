'use client';

import { useEffect, useRef, useState } from 'react';
import { apiClient, type PageResult, type ProductVO } from '@icedmall/api';
import { ProductCard } from '@icedmall/ui';
import { spuHref } from '@icedmall/utils';
import { LoadFailedState } from '@/app/components/load-failed-state';

interface Props {
  categoryId?: number;
  sort: string;
  initialProducts: ProductVO[];
  initialPage: number;
  totalPages: number;
}

const PAGE_SIZE = 12;

/**
 * 客户端无限滚动组件 — 从服务端初始数据开始, 触底加载更多
 *
 * ⚠️ 2026-10 B10：旧实现 `catch { /* ignore *\/ }` 把「加载更多失败」彻底吞掉 ——
 * 用户滑到底部没有任何反馈，也不知道还能重试（且 `hasMore` 仍为 true，滚动事件会
 * 反复静默失败）。现在：失败 → 底部渲染失败态 + 重试按钮，并**暂停**自动触底重试
 * （否则一次网络抖动会变成连发请求）。
 */
export function MarketplaceInfinite({ categoryId, sort, initialProducts, initialPage, totalPages }: Props) {
  const [page, setPage] = useState(initialPage);
  const [products, setProducts] = useState<ProductVO[]>(initialProducts);
  const [hasMore, setHasMore] = useState(initialPage < totalPages);
  const [loading, setLoading] = useState(false);
  /** 加载更多失败原因（与「已经到底了」严格区分）。 */
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);

  // 分类切换时重置
  useEffect(() => {
    setProducts(initialProducts);
    setPage(initialPage);
    setHasMore(initialPage < totalPages);
    setLoading(false);
    setLoadMoreError(null);
  }, [categoryId, sort, initialPage]);

  const loadMore = async () => {
    if (loading || !hasMore) return;
    const nextPage = page + 1;
    setLoading(true);
    setLoadMoreError(null);
    try {
      const sp = new URLSearchParams({ page: String(nextPage), size: String(PAGE_SIZE), sort, order: 'desc' });
      // 后端 `SpuPageReq` 经 `@QueryModel` 绑定（Jackson + 全局 SNAKE_CASE）→ 线上名 `category_id`
      // （契约 §P1）；camelCase `categoryId` 被静默忽略 → 无限滚动翻页会丢失品类筛选。
      if (categoryId) sp.set('category_id', String(categoryId));
      const result = await apiClient<PageResult<ProductVO>>(`/api/item/spu/page?${sp.toString()}`);
      setProducts((prev) => [...prev, ...result.records]);
      setPage(nextPage);
      setHasMore(nextPage < result.pages);
    } catch (e: unknown) {
      setLoadMoreError(e instanceof Error ? e.message : '网络异常');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    // 失败后暂停自动触底：等用户点「重试」，避免滚动事件连发请求
    if (loadMoreError) return;
    const observer = new IntersectionObserver(
      (entries) => { if (entries[0]?.isIntersecting) loadMore(); },
      { rootMargin: '300px' }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [page, hasMore, loading, categoryId, sort, loadMoreError]);

  return (
    <>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {products.map((p) => (
          // 回链 `/spu/{spu_id}`（业务键，B8 恢复）；`spuHref` 返回 null ⇒ 不渲染链接
          <ProductCard key={p.id} product={p} href={spuHref(p.spu_id) ?? undefined} />
        ))}
      </div>
      <div ref={sentinelRef} className="h-4 mt-4" />
      {loading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}
      {loadMoreError && (
        <div className="mt-6">
          <LoadFailedState
            compact
            title="加载更多失败"
            description="没能取到下一批商品（不是「已经到底了」）。"
            detail={loadMoreError}
            onRetry={() => { void loadMore(); }}
            retrying={loading}
          />
        </div>
      )}
      {!loadMoreError && !hasMore && products.length > 0 && (
        <p className="text-center text-xs text-warm-400 mt-8">— 已经到底了 —</p>
      )}
    </>
  );
}

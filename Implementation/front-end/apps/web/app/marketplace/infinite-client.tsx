'use client';

import { useEffect, useRef, useState } from 'react';
import { apiClient, type PageResult, type ProductVO } from '@icedmall/api';
import { ProductCard } from '@icedmall/ui';

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
 */
export function MarketplaceInfinite({ categoryId, sort, initialProducts, initialPage, totalPages }: Props) {
  const [page, setPage] = useState(initialPage);
  const [products, setProducts] = useState<ProductVO[]>(initialProducts);
  const [hasMore, setHasMore] = useState(initialPage < totalPages);
  const [loading, setLoading] = useState(false);
  const sentinelRef = useRef<HTMLDivElement>(null);

  // 分类切换时重置
  useEffect(() => {
    setProducts(initialProducts);
    setPage(initialPage);
    setHasMore(initialPage < totalPages);
    setLoading(false);
  }, [categoryId, sort, initialPage]);

  const loadMore = async () => {
    if (loading || !hasMore) return;
    const nextPage = page + 1;
    setLoading(true);
    try {
      const sp = new URLSearchParams({ page: String(nextPage), size: String(PAGE_SIZE), sort, order: 'desc' });
      if (categoryId) sp.set('categoryId', String(categoryId));
      const result = await apiClient<PageResult<ProductVO>>(`/api/item/product/page?${sp.toString()}`);
      setProducts((prev) => [...prev, ...result.records]);
      setPage(nextPage);
      setHasMore(nextPage < result.pages);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => { if (entries[0]?.isIntersecting) loadMore(); },
      { rootMargin: '300px' }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [page, hasMore, loading, categoryId, sort]);

  return (
    <>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {products.map((p) => (
          <ProductCard key={p.id} product={p} href={`/product/${p.id}`} />
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
      {!hasMore && products.length > 0 && (
        <p className="text-center text-xs text-warm-400 mt-8">— 已经到底了 —</p>
      )}
    </>
  );
}

'use client';

import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useInfiniteQuery } from '@tanstack/react-query';
import { apiClient, type PageResult, type ProductVO, type CategoryVO } from '@icedmall/api';
import { ProductCard, SectionReveal } from '@icedmall/ui';
import { useCategories } from '@icedmall/api';

const PAGE_SIZE = 12;

/**
 * 商城 — 分类筛选 + 无限滚动 (触底自动加载)
 */
export default function MarketplacePage() {
  const searchParams = useSearchParams();
  const categoryId = searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined;
  const sort = searchParams.get('sort') || 'sales';
  const sentinelRef = useRef<HTMLDivElement>(null);

  const { data: categories } = useCategories();

  // 无限查询 — 触底自动加载下一页
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isError,
  } = useInfiniteQuery<PageResult<ProductVO>>({
    queryKey: ['marketplace', 'products', categoryId, sort],
    queryFn: async ({ pageParam = 1 }) => {
      const sp = new URLSearchParams({
        page: String(pageParam),
        size: String(PAGE_SIZE),
        sort,
        order: 'desc',
      });
      if (categoryId) sp.set('categoryId', String(categoryId));
      return apiClient<PageResult<ProductVO>>(`/api/item/product/page?${sp.toString()}`);
    },
    getNextPageParam: (lastPage) => {
      const nextPage = lastPage.current + 1;
      return nextPage <= lastPage.pages ? nextPage : undefined;
    },
    initialPageParam: 1,
    staleTime: 30_000,
  });

  // IntersectionObserver — 触底加载
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage();
        }
      },
      { rootMargin: '300px' }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const allProducts = data?.pages.flatMap((p) => p.records) ?? [];
  const activeCategory = categories?.find((c) => c.id === categoryId);
  const total = data?.pages[0]?.total ?? 0;

  return (
    <div className="mx-auto max-w-7xl px-4 pb-20">
      {/* 分类 Tab 栏 */}
      {categories && (
        <SectionReveal>
          <div className="mt-8 flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
            <a
              href="/marketplace"
              className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
                !categoryId ? 'border-accent-green bg-accent-green text-white' : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white'
              }`}
            >
              全部
            </a>
            {categories.map((cat) => (
              <a
                key={cat.id}
                href={`/marketplace?categoryId=${cat.id}`}
                className={`shrink-0 px-4 py-2 text-sm rounded-full border transition-all ${
                  categoryId === cat.id ? 'border-accent-green bg-accent-green text-white' : 'border-warm-200 text-warm-600 hover:border-warm-400 bg-white'
                }`}
              >
                {cat.name}
              </a>
            ))}
          </div>
        </SectionReveal>
      )}

      {/* 标题 */}
      <SectionReveal delay={50}>
        <div className="mt-6 mb-4 flex items-baseline justify-between">
          <div>
            <h2 className="text-xl font-medium text-ink-black">
              {activeCategory ? activeCategory.name : '全部商品'}
            </h2>
            <p className="text-xs text-warm-400 mt-1">共 {total} 件</p>
          </div>
          <a
            href={`/marketplace?categoryId=${categoryId ?? ''}&sort=${sort === 'sales' ? 'newest' : 'sales'}`}
            className="text-sm text-warm-600 hover:text-accent-green transition-colors"
          >
            {sort === 'sales' ? '按热销' : '按最新'}
          </a>
        </div>
      </SectionReveal>

      {/* 错误 */}
      {isError && (
        <div className="text-center py-20">
          <p className="text-warm-600">加载失败，请检查后端服务是否启动</p>
        </div>
      )}

      {/* 加载中 */}
      {isLoading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}

      {/* 商品瀑布流 */}
      {!isLoading && allProducts.length > 0 && (
        <SectionReveal delay={100}>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {allProducts.map((product) => (
              <ProductCard key={product.id} product={product} href={`/product/${product.id}`} />
            ))}
          </div>
        </SectionReveal>
      )}

      {/* 触底哨兵 */}
      <div ref={sentinelRef} className="h-4 mt-4" />

      {/* 加载更多中 */}
      {isFetchingNextPage && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}

      {/* 到底了 */}
      {!hasNextPage && allProducts.length > 0 && !isLoading && (
        <p className="text-center text-xs text-warm-400 mt-8">— 已经到底了 —</p>
      )}

      {/* 空 */}
      {!isLoading && !isError && allProducts.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🍃</div>
          <p className="mt-4 text-warm-600">该分类暂无商品</p>
          <a href="/marketplace" className="mt-3 inline-block text-sm text-accent-green">查看全部商品 →</a>
        </div>
      )}
    </div>
  );
}

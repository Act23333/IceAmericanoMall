'use client';

import { useState, useDeferredValue, useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useSearch, useHotKeywords, useCategories } from '@icedmall/api';
import { ProductCard, SearchBar, SectionReveal } from '@icedmall/ui';

/**
 * 商品搜索页 — ES IK 分词搜索 + 热门关键词
 * TanStack Query 驱动, useDeferredValue 防抖
 */
export default function SearchPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const deferredKeyword = useDeferredValue(keyword);
  const [categoryId, setCategoryId] = useState<number | undefined>(
    searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined
  );
  const [page, setPage] = useState(Number(searchParams.get('page') ?? 1));
  const size = 20;

  const { data: categories } = useCategories();
  const { data: hotKeywords } = useHotKeywords(8);

  // 搜索页使用专用搜索 API (ES IK 分词)
  const { data, isLoading, isError, refetch } = useSearch(deferredKeyword, categoryId, page, size);

  // URL 同步
  useEffect(() => {
    const sp = new URLSearchParams();
    if (deferredKeyword) sp.set('keyword', deferredKeyword);
    if (categoryId) sp.set('categoryId', String(categoryId));
    if (page > 1) sp.set('page', String(page));
    const qs = sp.toString();
    router.replace(`/search${qs ? '?' + qs : ''}`, { scroll: false });
  }, [deferredKeyword, categoryId, page, router]);

  const totalPages = data ? Math.ceil(data.total / size) : 0;

  const handleSearch = (kw: string) => {
    setKeyword(kw);
    setPage(1);
    setCategoryId(undefined);
  };

  const handleHotClick = (kw: string) => {
    setKeyword(kw);
    setPage(1);
  };

  const handleCategoryClick = (catId: number | undefined) => {
    setCategoryId(catId);
    setPage(1);
  };

  return (
    <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
      {/* 搜索栏 */}
      <div className="max-w-xl mx-auto mb-6">
        <SearchBar placeholder="搜索商品、品牌、分类…" onSearch={handleSearch} />
      </div>

      {/* 热门搜索词 */}
      {hotKeywords && hotKeywords.length > 0 && (
        <div className="mb-6 flex flex-wrap items-center gap-2 justify-center">
          <span className="text-xs text-warm-400">热门搜索:</span>
          {hotKeywords.map((kw) => (
            <button
              key={kw}
              onClick={() => handleHotClick(kw)}
              className="px-3 py-1 text-xs rounded-full border border-warm-200 text-warm-600 hover:border-accent-green hover:text-accent-green transition-colors bg-white"
            >
              {kw}
            </button>
          ))}
        </div>
      )}

      {/* 分类筛选 */}
      {categories && categories.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          <button
            onClick={() => handleCategoryClick(undefined)}
            className={`px-3 py-1.5 text-xs rounded-full border transition-all ${
              !categoryId
                ? 'border-accent-gold bg-accent-gold/5 text-ink-black'
                : 'border-warm-200 text-warm-600 hover:border-warm-400'
            }`}
          >
            全部分类
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => handleCategoryClick(cat.id)}
              className={`px-3 py-1.5 text-xs rounded-full border transition-all ${
                categoryId === cat.id
                  ? 'border-accent-gold bg-accent-gold/5 text-ink-black'
                  : 'border-warm-200 text-warm-600 hover:border-warm-400'
              }`}
            >
              {cat.name}
            </button>
          ))}
        </div>
      )}

      {/* 结果计数 */}
      {data && (
        <p className="text-sm text-warm-600 mb-4">{keyword ? `搜索"${keyword}"` : '全部'} — 共 {data.total} 件商品</p>
      )}

      {/* 加载 */}
      {isLoading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}

      {/* 错误 */}
      {isError && (
        <div className="text-center py-20">
          <div className="text-4xl">☁️</div>
          <p className="mt-4 text-warm-600">搜索服务暂不可用</p>
          <button onClick={() => refetch()} className="mt-3 text-sm text-accent-green hover:underline">重试</button>
        </div>
      )}

      {/* 空结果 */}
      {!isLoading && !isError && data && data.records.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🍃</div>
          <h2 className="mt-4 text-lg font-medium text-ink-black">没有找到&quot;{keyword}&quot;相关商品</h2>
          <p className="mt-2 text-sm text-warm-600">试试其他关键词或浏览全部分类</p>
          <button onClick={() => { setKeyword(''); setCategoryId(undefined); }}
            className="mt-4 text-sm text-accent-green hover:underline">清除筛选</button>
        </div>
      )}

      {/* 结果 */}
      {!isLoading && !isError && data && data.records.length > 0 && (
        <SectionReveal>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {data.records.map((product) => (
              <ProductCard key={product.id} product={product} href={`/product/${product.id}`} />
            ))}
          </div>
        </SectionReveal>
      )}

      {/* 分页 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-8">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 bg-white text-ink-soft hover:border-warm-400 disabled:opacity-30 disabled:cursor-not-allowed">
            上一页
          </button>
          <span className="text-sm text-warm-600">{page} / {totalPages}</span>
          <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page >= totalPages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 bg-white text-ink-soft hover:border-warm-400 disabled:opacity-30 disabled:cursor-not-allowed">
            下一页
          </button>
        </div>
      )}
    </div>
  );
}

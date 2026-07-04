'use client';

import { useState, useCallback, useDeferredValue } from 'react';
import { useProducts, useCategories } from '@icedmall/api';
import { ProductCard, SearchBar, SectionReveal } from '@icedmall/ui';
import { useSearchParams, useRouter } from 'next/navigation';

const SORT_OPTIONS = [
  { value: 'sales-desc', label: '热销优先' },
  { value: 'price-asc', label: '价格从低到高' },
  { value: 'price-desc', label: '价格从高到低' },
  { value: 'newest-desc', label: '最新上架' },
];

/**
 * 商品搜索页 — Client Component
 *
 * TanStack Query 驱动的实时搜索 + 过滤 + 排序 + 分页
 * useDeferredValue 避免搜索输入卡顿
 */
export default function SearchPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const deferredKeyword = useDeferredValue(keyword);
  const [categoryId, setCategoryId] = useState<number | undefined>(
    searchParams.get('categoryId') ? Number(searchParams.get('categoryId')) : undefined,
  );
  const [sort, setSort] = useState(searchParams.get('sort') ?? 'sales');
  const [order, setOrder] = useState<'asc' | 'desc'>(
    (searchParams.get('order') as 'asc' | 'desc') ?? 'desc',
  );
  const [page, setPage] = useState(Number(searchParams.get('page') ?? 1));
  const size = 20;

  const { data: categories } = useCategories();
  const { data, isLoading, isError, refetch } = useProducts({
    keyword: deferredKeyword,
    categoryId,
    sort,
    order,
    page,
    size,
  });

  const handleSortChange = (value: string) => {
    const [newSort, newOrder] = value.split('-') as [string, 'asc' | 'desc'];
    setSort(newSort);
    setOrder(newOrder);
    setPage(1);
  };

  const totalPages = data ? Math.ceil(data.total / size) : 0;

  return (
    <div className="mx-auto max-w-7xl px-4 pt-24 pb-20">
      {/* ── 搜索栏 ── */}
      <div className="max-w-xl mx-auto mb-8">
        <SearchBar
          placeholder="搜索商品、品牌、分类…"
          onSearch={(v) => setKeyword(v)}
        />
      </div>

      {/* ── 筛选栏 ── */}
      <div className="flex flex-wrap items-center gap-3 mb-8">
        {/* 分类筛选 */}
        {categories && categories.length > 0 && (
          <div className="flex gap-2 flex-wrap">
            <button
              onClick={() => { setCategoryId(undefined); setPage(1); }}
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
                onClick={() => { setCategoryId(cat.id); setPage(1); }}
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
      </div>

      {/* ── 排序 + 结果计数 ── */}
      <div className="flex items-center justify-between mb-6">
        <p className="text-sm text-warm-600">
          {data ? `共 ${data.total} 件商品` : '搜索中…'}
        </p>
        <select
          value={`${sort}-${order}`}
          onChange={(e) => handleSortChange(e.target.value)}
          className="px-3 py-1.5 text-sm rounded-xl border border-warm-200 bg-white text-ink-soft outline-none focus:border-accent-green"
        >
          {SORT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {/* ── 商品网格 ── */}
      {isLoading && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="aspect-square rounded-2xl bg-warm-100 animate-glass-shimmer" />
          ))}
        </div>
      )}

      {isError && (
        <div className="text-center py-20">
          <div className="text-4xl">☁️</div>
          <p className="mt-4 text-warm-600">加载失败</p>
          <button
            onClick={() => refetch()}
            className="mt-3 text-sm text-accent-green hover:underline"
          >
            重试
          </button>
        </div>
      )}

      {!isLoading && !isError && data && data.records.length === 0 && (
        <div className="text-center py-20">
          <div className="text-6xl select-none">🍃</div>
          <h2 className="mt-4 text-lg font-medium text-ink-black">没有找到商品</h2>
          <p className="mt-2 text-sm text-warm-600">
            试试其他关键词或筛选条件
          </p>
        </div>
      )}

      {!isLoading && !isError && data && data.records.length > 0 && (
        <SectionReveal>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
            {data.records.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                variant="default"
                onClick={() => router.push(`/product/${product.id}`)}
              />
            ))}
          </div>
        </SectionReveal>
      )}

      {/* ── 分页 ── */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-12">
          <button
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            disabled={page <= 1}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 bg-white text-ink-soft hover:border-warm-400 disabled:opacity-30 disabled:cursor-not-allowed"
          >
            上一页
          </button>
          <span className="text-sm text-warm-600">
            {page} / {totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            disabled={page >= totalPages}
            className="px-3 py-1.5 text-sm rounded-lg border border-warm-200 bg-white text-ink-soft hover:border-warm-400 disabled:opacity-30 disabled:cursor-not-allowed"
          >
            下一页
          </button>
        </div>
      )}
    </div>
  );
}
